package dev.pixeldeck.net

import dev.pixeldeck.data.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class PcStatus { NotConfigured, Disconnected, Connecting, Connected, Rejected }

/**
 * Client for the PixelDeck Windows agent. Keeps one WebSocket open (with automatic reconnect),
 * authenticates with the shared token in the first "hello" message, and exposes request/ack calls.
 *
 * Wire format: {"v":1,"id":"...","type":"...","payload":{...}}
 */
class PcClient(private val scope: CoroutineScope) {
    private val http = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(4, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val _status = MutableStateFlow(PcStatus.NotConfigured)
    val status: StateFlow<PcStatus> = _status

    private val _pcName = MutableStateFlow<String?>(null)
    val pcName: StateFlow<String?> = _pcName

    private val _scripts = MutableStateFlow<List<String>>(emptyList())
    val scripts: StateFlow<List<String>> = _scripts

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    @Volatile private var socket: WebSocket? = null
    private var loop: Job? = null
    private var current: Triple<String, Int, String>? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Result<JsonObject>>>()

    /** Call whenever settings change. Restarts the connection only if the PC link settings changed. */
    @Synchronized
    fun configure(s: Settings) {
        val next = Triple(s.pcHost.trim(), s.pcPort, s.pcToken.trim())
        if (!s.pcConfigured) {
            loop?.cancel(); loop = null; current = null
            socket?.cancel()
            _status.value = PcStatus.NotConfigured
            return
        }
        if (next == current && loop?.isActive == true) return
        current = next
        loop?.cancel()
        socket?.cancel()
        loop = scope.launch {
            while (isActive) {
                connectOnce(next)
                // Rejected means the token is wrong: back off longer, the user must fix settings.
                delay(if (_status.value == PcStatus.Rejected) 30_000 else 3_000)
            }
        }
    }

    private suspend fun connectOnce(cfg: Triple<String, Int, String>) {
        val (host, port, token) = cfg
        val closed = CompletableDeferred<Unit>()
        _status.value = PcStatus.Connecting
        val request = Request.Builder().url("ws://$host:$port/").build()
        val ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(envelope("hello", "hello", buildJsonObject {
                    put("token", token)
                    put("client", "pixel8")
                }))
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.complete(Unit) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _lastError.value = t.message
                closed.complete(Unit)
            }
        })
        socket = ws
        try {
            closed.await()
        } finally {
            ws.cancel()
            if (socket === ws) socket = null
            if (_status.value != PcStatus.Rejected && current != null) _status.value = PcStatus.Disconnected
            pending.values.forEach { it.complete(Result.failure(IllegalStateException("PC disconnected"))) }
            pending.clear()
        }
    }

    private fun handle(text: String) {
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val id = o["id"]?.jsonPrimitive?.content ?: return
        val type = o["type"]?.jsonPrimitive?.content ?: return
        val payload = o["payload"] as? JsonObject ?: JsonObject(emptyMap())
        when (type) {
            "state" -> {
                _pcName.value = payload["name"]?.jsonPrimitive?.content
                _scripts.value = (payload["scripts"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
            }
            "ack" -> {
                if (id == "hello") { _status.value = PcStatus.Connected; _lastError.value = null }
                pending.remove(id)?.complete(Result.success(payload))
            }
            "error" -> {
                val msg = payload["message"]?.jsonPrimitive?.content ?: "error"
                if (id == "hello") { _status.value = PcStatus.Rejected; _lastError.value = msg }
                pending.remove(id)?.complete(Result.failure(IllegalStateException(msg)))
            }
        }
    }

    suspend fun request(type: String, payload: JsonObject = JsonObject(emptyMap()), timeoutMs: Long = 6_000): Result<JsonObject> {
        val ws = socket
        if (ws == null || _status.value != PcStatus.Connected) {
            return Result.failure(IllegalStateException("PC not connected"))
        }
        val id = UUID.randomUUID().toString().take(8)
        val d = CompletableDeferred<Result<JsonObject>>()
        pending[id] = d
        if (!ws.send(envelope(type, id, payload))) {
            pending.remove(id)
            return Result.failure(IllegalStateException("send failed"))
        }
        return withTimeoutOrNull(timeoutMs) { d.await() }
            ?: Result.failure<JsonObject>(IllegalStateException("PC did not respond")).also { pending.remove(id) }
    }

    suspend fun hotkey(keys: String) = request("hotkey", buildJsonObject { put("keys", keys) })
    suspend fun typeText(text: String) = request("type_text", buildJsonObject { put("text", text) })
    suspend fun media(action: String) = request("media", buildJsonObject { put("action", action) })
    suspend fun script(name: String) = request("script", buildJsonObject { put("name", name) }, 30_000)
    suspend fun launch(target: String, args: String = "") = request("launch", buildJsonObject {
        put("target", target)
        put("args", JsonArray(args.split(' ').filter { it.isNotBlank() }.map { JsonPrimitive(it) }))
    })

    private fun envelope(type: String, id: String, payload: JsonObject): String =
        buildJsonObject {
            put("v", 1)
            put("id", id)
            put("type", type)
            put("payload", payload)
        }.toString()
}
