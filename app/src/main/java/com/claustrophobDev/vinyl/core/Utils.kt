package com.claustrophobDev.vinyl.core

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.Base64

object Utils {

    // обычный URLDecoder меняет + на пробел и ломает пароли, поэтому свой
    fun urlDecode(s: String): String {
        if ('%' !in s) return s
        val bytes = s.toByteArray(UTF_8)
        val out = ByteArray(bytes.size)
        var len = 0
        var i = 0
        while (i < bytes.size) {
            if (bytes[i] == '%'.code.toByte() && i + 2 < bytes.size) {
                val hi = Character.digit(bytes[i + 1].toInt(), 16)
                val lo = Character.digit(bytes[i + 2].toInt(), 16)
                if (hi >= 0 && lo >= 0) {
                    out[len++] = (hi * 16 + lo).toByte()
                    i += 3
                    continue
                }
            }
            out[len++] = bytes[i]
            i++
        }
        return String(out, 0, len, UTF_8)
    }

    // base64 бывает обычный и url-safe, с = на конце и без
    fun fromBase64(s: String): String? {
        val str = s.filterNot { it.isWhitespace() }.replace('-', '+').replace('_', '/').trimEnd('=')
        if (str.isEmpty()) return null
        for (c in str) {
            val ok = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/'
            if (!ok) return null
        }
        val padded = str + "=".repeat((4 - str.length % 4) % 4)
        return try {
            String(Base64.getDecoder().decode(padded), UTF_8)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun sha1Short(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(s.toByteArray(UTF_8))
        return bytes.take(10).joinToString("") { "%02x".format(it) }
    }

    fun isIp(host: String): Boolean {
        return host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) || host.count { it == ':' } >= 2
    }
}

open class LinkException(msg: String) : IllegalArgumentException(msg)

class NotSupportedException(msg: String) : LinkException(msg)

// java.net.URI падает на пробелах и эмодзи в названии сервера, так что разбираем сами
class ProxyUri(
    val scheme: String,
    val userInfo: String,
    val host: String,
    val port: Int,
    params: Map<String, String>,
    val name: String
) : Params by MapParams(params) {

    companion object {
        fun parse(link: String): ProxyUri {
            val hashIdx = link.indexOf('#')
            val name = if (hashIdx >= 0) Utils.urlDecode(link.substring(hashIdx + 1)).trim() else ""
            val body = if (hashIdx >= 0) link.substring(0, hashIdx) else link

            val schemeEnd = body.indexOf("://")
            if (schemeEnd <= 0) throw LinkException("Некорректная ссылка")
            val scheme = body.substring(0, schemeEnd).lowercase()
            var rest = body.substring(schemeEnd + 3)

            var query = ""
            val qIdx = rest.indexOf('?')
            if (qIdx >= 0) {
                query = rest.substring(qIdx + 1)
                rest = rest.substring(0, qIdx)
            }

            val at = rest.lastIndexOf('@')
            val userInfo = if (at >= 0) rest.substring(0, at) else ""
            val hostPort = (if (at >= 0) rest.substring(at + 1) else rest).substringBefore('/')
            val (host, port) = splitHostPort(hostPort)

            val params = HashMap<String, String>()
            for (item in query.split('&')) {
                val key = item.substringBefore('=').trim()
                if (key.isNotEmpty()) params[key.lowercase()] = Utils.urlDecode(item.substringAfter('=', ""))
            }
            return ProxyUri(scheme, userInfo, host, port, params, name)
        }

        fun splitHostPort(s: String): Pair<String, Int> {
            // ipv6 в квадратных скобках, [::1]:443
            if (s.startsWith("[")) {
                val end = s.indexOf(']')
                if (end < 0) return Pair(s, -1)
                val port = s.substring(end + 1).removePrefix(":").toIntOrNull() ?: -1
                return Pair(s.substring(1, end), port)
            }
            if (s.count { it == ':' } != 1) return Pair(s, -1)
            return Pair(s.substringBefore(':'), s.substringAfter(':').toIntOrNull() ?: -1)
        }
    }
}
