package com.claustrophobDev.vinyl.core

import com.claustrophobDev.vinyl.data.AppMode
import com.claustrophobDev.vinyl.data.Routing
import com.claustrophobDev.vinyl.data.Settings
import org.json.JSONArray
import org.json.JSONObject

// конфиг под sing-box 1.14
// старый формат (dns с address, outbound dns/block, sniff в inbound) новое ядро уже не принимает!
object SingBoxConfig {

    private const val DIRECT = "direct"
    private const val DNS_REMOTE = "dns-remote"
    private const val DNS_LOCAL = "dns-local"

    val ruDomains = listOf(
        "ru", "su", "xn--p1ai", "yandex.net", "yandex.com", "yastatic.net", "vk.com", "vk.me", "userapi.com",
        "vkuser.net", "mycdn.me", "ok.ru", "mail.ru", "dzen.ru", "rutube.ru", "avito.st", "2gis.com"
    )

    val bankApps = listOf(
        "ru.sberbankmobile", "com.idamob.tinkoff.android", "ru.alfabank.mobile.android",
        "ru.vtb24.mobilebanking.android", "ru.raiffeisennews", "ru.gazprombank.android.mobilebank.app",
        "ru.sovcomcard.halva.v1", "ru.ozon.fintech.finance", "ru.nspk.mirpay", "ru.rostel"
    )

    fun build(proxy: Proxy, routing: Routing, settings: Settings, myPackage: String): String {
        val direct = ArrayList<String>()
        for (d in routing.directDomains) {
            cleanDomain(d)?.let { direct.add(it) }
        }
        if (routing.directRuSites) direct.addAll(ruDomains)
        val directDomains = direct.distinct()

        // dns
        val dnsServers = JSONArray()
            .put(JSONObject()
                .put("type", "https")
                .put("tag", DNS_REMOTE)
                .put("server", settings.dns.address)
                .put("detour", LinkParser.PROXY_TAG))
            .put(JSONObject().put("type", "local").put("tag", DNS_LOCAL))

        val dns = JSONObject()
        dns.put("servers", dnsServers)
        if (directDomains.isNotEmpty()) {
            val rule = JSONObject().put("domain_suffix", JSONArray(directDomains)).put("server", DNS_LOCAL)
            dns.put("rules", JSONArray().put(rule))
        }
        dns.put("final", DNS_REMOTE)
        dns.put("strategy", if (settings.ipv6) "prefer_ipv4" else "ipv4_only")

        // маршруты
        val rules = JSONArray()
        rules.put(JSONObject().put("action", "sniff"))
        rules.put(JSONObject()
            .put("type", "logical")
            .put("mode", "or")
            .put("rules", JSONArray().put(JSONObject().put("protocol", "dns")).put(JSONObject().put("port", 53)))
            .put("action", "hijack-dns"))
        rules.put(JSONObject().put("ip_is_private", true).put("outbound", DIRECT))
        if (!settings.ipv6) {
            // ipv6 все равно заворачиваем в туннель чтобы не было утечки, но сразу режем
            // иначе телега долго тупит на таймаутах прежде чем пойти по ipv4
            rules.put(JSONObject().put("ip_version", 6).put("action", "reject"))
        }
        if (directDomains.isNotEmpty()) {
            rules.put(JSONObject().put("domain_suffix", JSONArray(directDomains)).put("outbound", DIRECT))
        }

        val route = JSONObject()
            .put("rules", rules)
            .put("final", LinkParser.PROXY_TAG)
            .put("auto_detect_interface", true)
            .put("default_domain_resolver", DNS_LOCAL)

        val config = JSONObject()
        config.put("log", JSONObject().put("level", "warn").put("timestamp", false))
        config.put("dns", dns)
        config.put("inbounds", JSONArray().put(tun(routing, myPackage)))
        val directOut = JSONObject().put("type", "direct").put("tag", DIRECT)
        if (proxy.isEndpoint) {
            // wireguard идет в endpoints, тег все тот же proxy, так что маршруты не меняются
            config.put("endpoints", JSONArray().put(proxy.json))
            config.put("outbounds", JSONArray().put(directOut))
        } else {
            config.put("outbounds", JSONArray().put(proxy.json).put(directOut))
        }
        config.put("route", route)
        return config.toString()
    }

    private fun tun(routing: Routing, myPackage: String): JSONObject {
        val tun = JSONObject()
            .put("type", "tun")
            .put("tag", "tun-in")
            .put("address", JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126"))
            .put("mtu", 9000)
            .put("auto_route", true)
            .put("strict_route", true)
            .put("stack", "mixed")
        val (include, exclude) = packages(routing, myPackage)
        if (include.isNotEmpty()) tun.put("include_package", JSONArray(include))
        if (exclude.isNotEmpty()) tun.put("exclude_package", JSONArray(exclude))
        return tun
    }

    // ВАЖНО: само приложение из туннеля НЕ исключать
    // tcp стек ядра принимает соединения сокетом нашего процесса, и если нас исключить,
    // ответы уходят мимо tun и весь tcp просто висит (долго искал почему тг не грузит)
    // петли не будет, исходящие сокеты ядра и так идут через protect()
    fun packages(routing: Routing, myPackage: String): Pair<List<String>, List<String>> {
        val banks = if (routing.bypassBanks) bankApps else emptyList()
        return when (routing.appMode) {
            AppMode.ONLY -> {
                val selected = routing.apps.filter { it != myPackage && it !in banks }.sorted()
                if (selected.isNotEmpty()) Pair(selected + myPackage, emptyList())
                else Pair(emptyList(), banks)
            }
            AppMode.EXCEPT -> Pair(emptyList(), (routing.apps.filter { it != myPackage }.sorted() + banks).distinct())
            AppMode.ALL -> Pair(emptyList(), banks)
        }
    }

    // https://Site.ru/path -> site.ru
    fun cleanDomain(input: String): String? {
        val domain = input.trim().lowercase()
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore(':')
            .removePrefix("*.")
            .trim('.')
        if (domain.isEmpty() || domain.any { it.isWhitespace() }) return null
        return domain
    }
}
