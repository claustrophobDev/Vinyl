package com.claustrophobDev.vinyl.core

import com.claustrophobDev.vinyl.data.Protocol
import com.claustrophobDev.vinyl.data.Server
import org.json.JSONArray
import org.json.JSONObject

interface Params {
    fun q(vararg keys: String): String?
    fun flag(vararg keys: String): Boolean
}

class MapParams(map: Map<String, String>) : Params {
    private val map = map.mapKeys { it.key.lowercase() }

    // регистр ключа не важен, пустое значение считаем что его нет
    override fun q(vararg keys: String): String? {
        for (key in keys) {
            val v = map[key.lowercase()]?.trim()
            if (!v.isNullOrEmpty()) return v
        }
        return null
    }

    override fun flag(vararg keys: String): Boolean {
        val v = q(*keys)?.lowercase()
        return v == "1" || v == "true" || v == "yes"
    }
}

class ParsedLink(
    val protocol: Protocol,
    val name: String,
    val host: String,
    val port: Int,
    val outbound: JSONObject?,
    val unsupported: String?,
    // wireguard в sing-box 1.14 это endpoint, а не обычный outbound, кладется в другую секцию конфига
    val isEndpoint: Boolean = false
)

// готовый прокси для конфига: json и в какую секцию его класть
class Proxy(val json: JSONObject, val isEndpoint: Boolean)

// ссылки vless/vmess/trojan/ss/hy2/tuic -> outbound для sing-box
object LinkParser {

    const val PROXY_TAG = "proxy"

    private val schemes = mapOf(
        "vless" to Protocol.VLESS,
        "vmess" to Protocol.VMESS,
        "trojan" to Protocol.TROJAN,
        "ss" to Protocol.SHADOWSOCKS,
        "hysteria2" to Protocol.HYSTERIA2,
        "hy2" to Protocol.HYSTERIA2,
        "tuic" to Protocol.TUIC,
        "wireguard" to Protocol.WIREGUARD,
        "wg" to Protocol.WIREGUARD
    )

    private val fingerprints = setOf("chrome", "firefox", "edge", "safari", "360", "qq", "ios", "android", "random", "randomized")

    fun isProxyLink(s: String) = s.substringBefore("://", "").lowercase() in schemes

    fun parse(link: String): ParsedLink {
        val s = link.trim()
        return when (schemes[s.substringBefore("://", "").lowercase()]) {
            Protocol.VLESS -> vless(s)
            Protocol.VMESS -> vmess(s)
            Protocol.TROJAN -> trojan(s)
            Protocol.SHADOWSOCKS -> shadowsocks(s)
            Protocol.HYSTERIA2 -> hysteria2(s)
            Protocol.TUIC -> tuic(s)
            Protocol.WIREGUARD -> wireguard(s)
            null -> throw LinkException("Неизвестный тип ссылки")
        }
    }

    fun toProxy(link: String): Proxy {
        val p = parse(link)
        val json = p.outbound ?: throw NotSupportedException(p.unsupported ?: "Сервер не поддерживается")
        return Proxy(json, p.isEndpoint)
    }

    fun toOutbound(link: String): JSONObject = toProxy(link).json

    fun toServer(link: String, subId: String?): Server? {
        val p = try {
            parse(link)
        } catch (e: Exception) {
            return null
        }
        val (flag, name) = Flags.split(p.name.ifBlank { "${p.protocol.label} ${p.host}" })
        return Server(
            id = Utils.sha1Short((subId ?: "manual") + "|" + link.trim()),
            name = name,
            protocol = p.protocol,
            host = p.host,
            port = p.port,
            link = link.trim(),
            flag = flag,
            subscriptionId = subId,
            unsupported = p.unsupported
        )
    }

    // тело подписки: base64 или просто текст, одна ссылка на строку
    fun parseSubscription(text: String, subId: String?): List<Server> {
        val raw = text.trim().removePrefix(Char(0xFEFF).toString())
        val hasLinks = raw.lineSequence().any { isProxyLink(it.trim()) }
        val content = if (hasLinks) raw else Utils.fromBase64(raw) ?: raw
        return content.lineSequence()
            .map { it.trim() }
            .filter { isProxyLink(it) }
            .mapNotNull { toServer(it, subId) }
            .distinctBy { it.id }
            .toList()
    }

    // если внутри кинули NotSupportedException, сервер все равно показываем но серым
    private inline fun make(protocol: Protocol, name: String, host: String, port: Int, endpoint: Boolean = false, build: () -> JSONObject): ParsedLink {
        return try {
            ParsedLink(protocol, name, host, port, build(), null, endpoint)
        } catch (e: NotSupportedException) {
            ParsedLink(protocol, name, host, port, null, e.message, endpoint)
        }
    }

    private fun checkAddress(host: String, port: Int) {
        if (host.isBlank()) throw LinkException("В ссылке не указан адрес сервера")
        if (port !in 1..65535) throw LinkException("В ссылке не указан порт")
    }

    private fun outbound(type: String, host: String, port: Int): JSONObject {
        return JSONObject()
            .put("type", type)
            .put("tag", PROXY_TAG)
            .put("server", host)
            .put("server_port", port)
    }

    private fun vless(link: String): ParsedLink {
        val u = ProxyUri.parse(link)
        val uuid = Utils.urlDecode(u.userInfo).trim()
        checkAddress(u.host, u.port)
        if (uuid.isEmpty()) throw LinkException("В ссылке нет UUID")
        return make(Protocol.VLESS, u.name, u.host, u.port) {
            val out = outbound("vless", u.host, u.port)
            out.put("uuid", uuid)
            val flow = u.q("flow")
            if (flow != null && flow.startsWith("xtls-rprx-vision")) out.put("flow", "xtls-rprx-vision")
            out.put("packet_encoding", "xudp")
            transport(u)?.let { out.put("transport", it) }
            tls(u, u.host, false)?.let { out.put("tls", it) }
            out
        }
    }

    private fun vmess(link: String): ParsedLink {
        val b64 = link.substringAfter("://").substringBefore('#').trim()
        val decoded = Utils.fromBase64(b64) ?: throw LinkException("Некорректная VMess-ссылка")
        val json = try {
            JSONObject(decoded)
        } catch (e: Exception) {
            throw LinkException("Некорректная VMess-ссылка")
        }

        val host = json.optString("add").trim()
        val port = json.optString("port").trim().toIntOrNull() ?: -1
        val uuid = json.optString("id").trim()
        checkAddress(host, port)
        if (uuid.isEmpty()) throw LinkException("В ссылке нет UUID")

        // у vmess все лежит в json, перекладываем в такие же параметры как у vless и дальше общий код
        val net = json.optString("net").lowercase().ifEmpty { "tcp" }
        val params = MapParams(mapOf(
            "security" to (if (json.optString("tls").equals("tls", true)) "tls" else ""),
            "sni" to json.optString("sni"),
            "alpn" to json.optString("alpn"),
            "fp" to json.optString("fp"),
            "type" to (if (net == "h2") "http" else net),
            "host" to json.optString("host"),
            "path" to json.optString("path"),
            "serviceName" to json.optString("path"),
            "headerType" to json.optString("type"),
            "allowInsecure" to json.optString("allowInsecure")
        ))

        return make(Protocol.VMESS, json.optString("ps").trim(), host, port) {
            val out = outbound("vmess", host, port)
            out.put("uuid", uuid)
            out.put("security", json.optString("scy").ifBlank { "auto" })
            out.put("alter_id", json.optString("aid").trim().toIntOrNull() ?: 0)
            transport(params)?.let { out.put("transport", it) }
            tls(params, host, false)?.let { out.put("tls", it) }
            out
        }
    }

    private fun trojan(link: String): ParsedLink {
        val u = ProxyUri.parse(link)
        val password = Utils.urlDecode(u.userInfo)
        checkAddress(u.host, u.port)
        if (password.isEmpty()) throw LinkException("В ссылке нет пароля")
        return make(Protocol.TROJAN, u.name, u.host, u.port) {
            val out = outbound("trojan", u.host, u.port)
            out.put("password", password)
            transport(u)?.let { out.put("transport", it) }
            tls(u, u.host, true)?.let { out.put("tls", it) }
            out
        }
    }

    private fun shadowsocks(link: String): ParsedLink {
        val u = ProxyUri.parse(link)
        var host = u.host
        var port = u.port
        val creds: String
        if (u.userInfo.isNotEmpty()) {
            // ss://method:pass@host:port или ss://base64(method:pass)@host:port
            val info = Utils.urlDecode(u.userInfo)
            creds = if (':' in info) info else Utils.fromBase64(info) ?: throw LinkException("Некорректная Shadowsocks-ссылка")
        } else {
            // старый формат, вообще все в base64
            val encoded = link.substringAfter("://").substringBefore('#').substringBefore('?').trimEnd('/')
            val decoded = Utils.fromBase64(encoded) ?: throw LinkException("Некорректная Shadowsocks-ссылка")
            creds = decoded.substringBeforeLast('@')
            val hp = ProxyUri.splitHostPort(decoded.substringAfterLast('@'))
            host = hp.first
            port = hp.second
        }

        val method = creds.substringBefore(':').trim()
        val password = creds.substringAfter(':', "")
        checkAddress(host, port)
        if (method.isEmpty() || password.isEmpty()) throw LinkException("Некорректная Shadowsocks-ссылка")

        return make(Protocol.SHADOWSOCKS, u.name, host, port) {
            val out = outbound("shadowsocks", host, port)
            out.put("method", method)
            out.put("password", password)
            val plugin = u.q("plugin")
            if (plugin != null) {
                var pluginName = plugin.substringBefore(';').trim()
                if (pluginName == "simple-obfs") pluginName = "obfs-local"
                if (pluginName != "obfs-local" && pluginName != "v2ray-plugin") {
                    throw NotSupportedException("Плагин $pluginName не поддерживается")
                }
                out.put("plugin", pluginName)
                out.put("plugin_opts", plugin.substringAfter(';', ""))
            }
            out
        }
    }

    private fun hysteria2(link: String): ParsedLink {
        val u = ProxyUri.parse(link)
        val port = if (u.port in 1..65535) u.port else 443
        checkAddress(u.host, port)
        val password = Utils.urlDecode(u.userInfo)
        return make(Protocol.HYSTERIA2, u.name, u.host, port) {
            val out = outbound("hysteria2", u.host, port)
            if (password.isNotEmpty()) out.put("password", password)

            // port hopping, mport=20000-30000 надо превратить в "20000:30000"
            val ports = u.q("mport")?.split(',')
                ?.map { it.trim().replace('-', ':') }
                ?.filter { it.isNotEmpty() }
                ?.map { if (':' in it) it else "$it:$it" }
            if (!ports.isNullOrEmpty()) {
                out.remove("server_port")
                out.put("server_ports", JSONArray(ports))
            }

            if (u.q("obfs").equals("salamander", ignoreCase = true)) {
                out.put("obfs", JSONObject()
                    .put("type", "salamander")
                    .put("password", u.q("obfs-password", "obfsPassword") ?: ""))
            }
            out.put("tls", quicTls(u, u.host, null))
            out
        }
    }

    private fun tuic(link: String): ParsedLink {
        val u = ProxyUri.parse(link)
        checkAddress(u.host, u.port)
        val info = Utils.urlDecode(u.userInfo)
        val uuid = info.substringBefore(':').trim()
        if (uuid.isEmpty()) throw LinkException("В ссылке нет UUID")
        return make(Protocol.TUIC, u.name, u.host, u.port) {
            val out = outbound("tuic", u.host, u.port)
            out.put("uuid", uuid)
            val pass = info.substringAfter(':', "")
            if (pass.isNotEmpty()) out.put("password", pass)
            out.put("congestion_control", u.q("congestion_control", "congestion")?.lowercase() ?: "bbr")
            val relay = u.q("udp_relay_mode")?.lowercase()
            if (relay == "native" || relay == "quic") out.put("udp_relay_mode", relay)
            out.put("tls", quicTls(u, u.host, "h3"))
            out
        }
    }

    // формат wireguard://<приватный ключ>@<хост>:<порт>?publickey=..&address=..&reserved=.. (как в hiddify и панелях)
    private fun wireguard(link: String): ParsedLink {
        val u = ProxyUri.parse(link)
        checkAddress(u.host, u.port)
        val privateKey = Utils.urlDecode(u.userInfo).trim()
        if (privateKey.isEmpty()) throw LinkException("В ссылке нет приватного ключа")
        val publicKey = u.q("publickey", "public_key", "peerpublickey", "pubkey")
            ?: throw LinkException("В ссылке нет публичного ключа пира")
        val addresses = u.q("address", "ip", "addresses")
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: throw LinkException("В ссылке нет адреса интерфейса")

        return make(Protocol.WIREGUARD, u.name, u.host, u.port, endpoint = true) {
            val peer = JSONObject()
                .put("address", u.host)
                .put("port", u.port)
                .put("public_key", publicKey)
                .put("allowed_ips", JSONArray().put("0.0.0.0/0").put("::/0"))
            u.q("presharedkey", "pre_shared_key", "psk")?.let { peer.put("pre_shared_key", it) }
            reserved(u.q("reserved"))?.let { peer.put("reserved", it) }
            u.q("keepalive", "persistent_keepalive")?.toIntOrNull()?.let { peer.put("persistent_keepalive_interval", it) }

            JSONObject()
                .put("type", "wireguard")
                .put("tag", PROXY_TAG)
                .put("mtu", u.q("mtu")?.toIntOrNull() ?: 1408)
                .put("address", JSONArray(addresses))
                .put("private_key", privateKey)
                .put("peers", JSONArray().put(peer))
        }
    }

    // reserved бывает как "1,2,3" или как base64 из 3 байт
    private fun reserved(value: String?): JSONArray? {
        if (value.isNullOrEmpty()) return null
        val nums = value.split(',').mapNotNull { it.trim().toIntOrNull() }
        if (nums.size == 3) return JSONArray(nums)
        val bytes = try {
            java.util.Base64.getDecoder().decode(value.trim())
        } catch (e: Exception) {
            null
        }
        if (bytes != null && bytes.size == 3) {
            return JSONArray(listOf(bytes[0].toInt() and 0xff, bytes[1].toInt() and 0xff, bytes[2].toInt() and 0xff))
        }
        return null
    }

    private fun quicTls(p: Params, host: String, defaultAlpn: String?): JSONObject {
        val tls = JSONObject().put("enabled", true)
        val sni = p.q("sni", "peer") ?: if (Utils.isIp(host)) null else host
        if (sni != null) tls.put("server_name", sni)
        if (p.flag("insecure", "allowInsecure", "allow_insecure")) tls.put("insecure", true)
        val alpn = p.q("alpn")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOfNotNull(defaultAlpn)
        if (alpn.isNotEmpty()) tls.put("alpn", JSONArray(alpn))
        return tls
    }

    private fun tls(p: Params, host: String, forceTls: Boolean): JSONObject? {
        val security = p.q("security")?.lowercase() ?: ""
        val enabled = when (security) {
            "tls", "reality", "xtls" -> true
            "none" -> false
            else -> forceTls
        }
        if (!enabled) return null

        val tls = JSONObject().put("enabled", true)

        var sni = p.q("sni", "peer", "serverName")
        if (sni == null) sni = p.q("host")?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
        if (sni == null && !Utils.isIp(host)) sni = host
        if (sni != null) tls.put("server_name", sni)

        if (p.flag("allowInsecure", "insecure", "allow_insecure")) tls.put("insecure", true)

        val alpn = p.q("alpn")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        if (!alpn.isNullOrEmpty()) tls.put("alpn", JSONArray(alpn))

        // fp=none значит без utls, всякие непонятные значения заменяем на chrome
        val rawFp = p.q("fp", "fingerprint")?.lowercase()
        val fp = if (rawFp == null || rawFp == "none") null else if (rawFp in fingerprints) rawFp else "chrome"

        if (security == "reality") {
            val pbk = p.q("pbk", "publicKey") ?: throw LinkException("В Reality-ссылке нет публичного ключа")
            // reality без utls ядро не запускает
            tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", fp ?: "chrome"))
            val reality = JSONObject().put("enabled", true).put("public_key", pbk)
            p.q("sid", "shortId")?.let { reality.put("short_id", it) }
            tls.put("reality", reality)
        } else if (fp != null) {
            tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", fp))
        }
        return tls
    }

    private fun transport(p: Params): JSONObject? {
        val type = p.q("type", "net")?.lowercase() ?: "tcp"
        return when (type) {
            "tcp", "raw", "none" -> if (p.q("headerType")?.lowercase() == "http") http(p) else null
            "ws", "websocket" -> ws(p)
            "grpc", "gun" -> JSONObject().put("type", "grpc").put("service_name", p.q("serviceName", "path") ?: "")
            "httpupgrade" -> {
                val t = JSONObject().put("type", "httpupgrade").put("path", fixPath(p.q("path")))
                p.q("host")?.let { t.put("host", it) }
                t
            }
            "http", "h2" -> http(p)
            "quic" -> JSONObject().put("type", "quic")
            "xhttp", "splithttp" -> throw NotSupportedException("XHTTP не поддерживается ядром sing-box")
            "kcp", "mkcp" -> throw NotSupportedException("mKCP не поддерживается ядром sing-box")
            else -> throw NotSupportedException("Транспорт $type не поддерживается")
        }
    }

    private fun http(p: Params): JSONObject {
        val t = JSONObject().put("type", "http")
        val hosts = p.q("host")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
        if (!hosts.isNullOrEmpty()) t.put("host", JSONArray(hosts))
        p.q("path")?.let { t.put("path", fixPath(it)) }
        return t
    }

    private fun ws(p: Params): JSONObject {
        // в path бывает ?ed=2048, это early data, в sing-box оно отдельным полем
        val fullPath = p.q("path") ?: "/"
        var path = fullPath
        var earlyData = 0
        val qIdx = fullPath.indexOf('?')
        if (qIdx >= 0) {
            val rest = ArrayList<String>()
            for (param in fullPath.substring(qIdx + 1).split('&')) {
                val ed = if (param.startsWith("ed=")) param.removePrefix("ed=").toIntOrNull() else null
                if (ed != null) {
                    earlyData = ed
                } else if (param.isNotEmpty()) {
                    rest.add(param)
                }
            }
            path = fullPath.substring(0, qIdx) + if (rest.isEmpty()) "" else "?" + rest.joinToString("&")
        }

        val t = JSONObject().put("type", "ws").put("path", fixPath(path))
        p.q("host")?.let { t.put("headers", JSONObject().put("Host", it)) }
        if (earlyData > 0) {
            t.put("max_early_data", earlyData)
            t.put("early_data_header_name", "Sec-WebSocket-Protocol")
        }
        return t
    }

    private fun fixPath(path: String?): String {
        val p = path?.trim() ?: ""
        return if (p.startsWith("/")) p else "/$p"
    }
}
