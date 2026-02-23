package com.claustrophobDev.vinyl.data

import androidx.compose.runtime.Immutable

enum class Protocol(val label: String) {
    VLESS("VLESS"),
    VMESS("VMess"),
    TROJAN("Trojan"),
    SHADOWSOCKS("Shadowsocks"),
    HYSTERIA2("Hysteria2"),
    TUIC("TUIC"),
    WIREGUARD("WireGuard");

    // эти работают по udp, tcp пинг до них не пройдет
    val isUdp: Boolean
        get() = this == HYSTERIA2 || this == TUIC || this == WIREGUARD
}

@Immutable
data class Server(
    val id: String,
    val name: String,
    val protocol: Protocol,
    val host: String,
    val port: Int,
    val link: String,
    val flag: String,
    val subscriptionId: String? = null,
    val unsupported: String? = null // если не null то sing-box такой сервер не умеет (xhttp и тд)
)

@Immutable
data class Subscription(
    val id: String,
    val name: String,
    val url: String,
    val updatedAt: Long = 0,
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val totalBytes: Long = 0,
    val expireAt: Long = 0
)

enum class AppMode(val title: String, val hint: String) {
    ALL("Все", "Весь трафик телефона идет через VPN"),
    ONLY("Только", "Через VPN идут только выбранные приложения"),
    EXCEPT("Кроме", "Выбранные приложения работают без VPN")
}

@Immutable
data class Routing(
    val appMode: AppMode = AppMode.ALL,
    val apps: Set<String> = emptySet(),
    val directDomains: List<String> = emptyList(),
    val bypassBanks: Boolean = true,
    val directRuSites: Boolean = false
)

enum class RemoteDns(val title: String, val address: String) {
    CLOUDFLARE("Cloudflare", "1.1.1.1"),
    GOOGLE("Google", "8.8.8.8"),
    QUAD9("Quad9", "9.9.9.9")
}

@Immutable
data class Settings(
    val ipv6: Boolean = false,
    val dns: RemoteDns = RemoteDns.CLOUDFLARE,
    val autoConnect: Boolean = false
)

@Immutable
data class AppState(
    val servers: List<Server> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val selectedId: String = AUTO_SERVER_ID,
    val routing: Routing = Routing(),
    val settings: Settings = Settings()
)

const val AUTO_SERVER_ID = "auto"

// id сервера -> пинг. нет ключа = еще не пинговали, -1 не ответил, -2 udp
typealias PingMap = Map<String, Int>

data class AppInfo(val packageName: String, val label: String)
