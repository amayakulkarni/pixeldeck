package dev.pixeldeck.iot

import dev.pixeldeck.data.HaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Minimal Home Assistant REST client (long-lived access token). */
class HaClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private fun base(url: String) = url.trim().trimEnd('/')

    suspend fun states(url: String, token: String): Result<List<HaEntity>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${base(url)}/api/states")
                .header("Authorization", "Bearer ${token.trim()}").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Home Assistant returned HTTP ${resp.code}")
                json.decodeFromString(ListSerializer(HaEntity.serializer()), resp.body!!.string())
            }
        }
    }

    suspend fun call(url: String, token: String, domain: String, service: String, entityId: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject { if (!entityId.isNullOrBlank()) put("entity_id", entityId) }.toString()
                val req = Request.Builder().url("${base(url)}/api/services/$domain/$service")
                    .header("Authorization", "Bearer ${token.trim()}")
                    .post(body.toRequestBody("application/json".toMediaType())).build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("Home Assistant returned HTTP ${resp.code}")
                }
            }
        }

    /** Picks the service that makes sense when a tile is tapped. */
    fun tapService(entity: HaEntity): Pair<String, String>? = when (entity.domain) {
        "light", "switch", "fan", "input_boolean", "cover", "humidifier" -> entity.domain to "toggle"
        "lock" -> "lock" to if (entity.state == "locked") "unlock" else "lock"
        "scene", "script" -> entity.domain to "turn_on"
        "automation" -> "automation" to "trigger"
        "button", "input_button" -> entity.domain to "press"
        else -> null
    }

    companion object {
        /** Domains worth showing as tiles. */
        val CONTROLLABLE = setOf(
            "light", "switch", "fan", "input_boolean", "cover", "lock", "scene", "script",
            "automation", "button", "climate", "media_player", "sensor", "binary_sensor",
        )
    }
}
