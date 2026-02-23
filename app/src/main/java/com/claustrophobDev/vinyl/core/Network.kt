package com.claustrophobDev.vinyl.core

import android.net.Network
import com.claustrophobDev.vinyl.data.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

class LoadedSub(
    val servers: List<Server>,
    val title: String?,
    val upload: Long,
    val download: Long,
    val total: Long,
    val expire: Long
)

object SubLoader {

    suspend fun load(url: String, subId: String): LoadedSub = withContext(Dispatchers.IO) {
        var current = url.substringBefore('#').trim()
        // редиректы обрабатываем сами, HttpURLConnection не переходит между http и https
        repeat(6) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = false
            // с таким user-agent панели (marzban, remnawave, 3x-ui) отдают обычный список ссылок
            conn.setRequestProperty("User-Agent", "v2rayNG/1.10.5")
            conn.setRequestProperty("Accept", "*/*")
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: throw IOException("Пустой редирект")
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code !in 200..299) throw IOException("Сервер подписки ответил кодом $code")

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val servers = LinkParser.parseSubscription(body, subId)
                if (servers.isEmpty()) throw LinkException("В подписке не найдено поддерживаемых серверов")

                val info = parseUserInfo(conn.getHeaderField("subscription-userinfo"))
                return@withContext LoadedSub(
                    servers,
                    parseTitle(conn.getHeaderField("profile-title")),
                    info["upload"] ?: 0L,
                    info["download"] ?: 0L,
                    info["total"] ?: 0L,
                    (info["expire"] ?: 0L) * 1000
                )
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Слишком много редиректов")
    }

    // заголовок вида: upload=123; download=456; total=789; expire=1700000000
    fun parseUserInfo(header: String?): Map<String, Long> {
        val result = HashMap<String, Long>()
        if (header == null) return result
        for (part in header.split(';')) {
            val key = part.substringBefore('=').trim().lowercase()
            val value = part.substringAfter('=', "").trim().toDoubleOrNull()?.toLong()
            if (key.isNotEmpty() && value != null) result[key] = value
        }
        return result
    }

    fun parseTitle(header: String?): String? {
        val v = header?.trim()
        if (v.isNullOrEmpty()) return null
        if (v.startsWith("base64:")) return Utils.fromBase64(v.removePrefix("base64:"))?.trim()
        return v
    }
}

object Ping {
    const val TIMEOUT = -1
    const val UDP = -2

    // network это настоящая сеть, чтобы при включенном впн пинг шел напрямую а не через прокси
    suspend fun tcp(host: String, port: Int, network: Network?, timeout: Int = 2500): Int = withContext(Dispatchers.IO) {
        try {
            val addr = network?.getAllByName(host)?.firstOrNull() ?: InetAddress.getByName(host)
            Socket().use { socket ->
                network?.bindSocket(socket)
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(addr, port), timeout)
                ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1)
            }
        } catch (e: Exception) {
            TIMEOUT
        }
    }

    suspend fun pingAll(servers: List<Server>, network: Network?, onResult: (String, Int) -> Unit) = coroutineScope {
        val limit = Semaphore(24)
        val jobs = servers.filter { it.unsupported == null }.map { s ->
            launch {
                limit.withPermit {
                    // hy2 и tuic на udp, на tcp они не ответят
                    val ms = if (s.protocol.isUdp) UDP else tcp(s.host, s.port, network)
                    onResult(s.id, ms)
                }
            }
        }
        jobs.joinAll()
    }
}
