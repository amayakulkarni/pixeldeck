package dev.pixeldeck.net

import java.net.URLDecoder

/** What the Windows agent encodes in its QR code: `pixeldeck://host:port/token?n=PC%20name`. */
data class Pairing(val host: String, val port: Int, val token: String, val name: String = "") {
    companion object {
        private val REGEX = Regex("""pixeldeck://([^:/\s]+):(\d{1,5})/([0-9a-fA-F]+)(?:\?n=([^\s&]*))?""")

        fun parse(text: String): Pairing? {
            val m = REGEX.find(text.trim()) ?: return null
            val port = m.groupValues[2].toInt()
            if (port !in 1..65535) return null
            val name = m.groupValues[4].let { raw ->
                if (raw.isEmpty()) "" else runCatching { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
            }
            return Pairing(m.groupValues[1], port, m.groupValues[3], name)
        }
    }
}
