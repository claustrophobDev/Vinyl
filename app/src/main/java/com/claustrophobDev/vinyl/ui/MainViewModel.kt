package com.claustrophobDev.vinyl.ui

import android.app.Application
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.claustrophobDev.vinyl.VinylApp
import com.claustrophobDev.vinyl.core.LinkParser
import com.claustrophobDev.vinyl.core.SingBoxConfig
import com.claustrophobDev.vinyl.core.SubLoader
import com.claustrophobDev.vinyl.core.Utils
import com.claustrophobDev.vinyl.data.AUTO_SERVER_ID
import com.claustrophobDev.vinyl.data.AppMode
import com.claustrophobDev.vinyl.data.AppState
import com.claustrophobDev.vinyl.data.PingMap
import com.claustrophobDev.vinyl.data.Routing
import com.claustrophobDev.vinyl.data.Settings
import com.claustrophobDev.vinyl.data.Subscription
import com.claustrophobDev.vinyl.vpn.Traffic
import com.claustrophobDev.vinyl.vpn.VinylVpnService
import com.claustrophobDev.vinyl.vpn.VpnLog
import com.claustrophobDev.vinyl.vpn.VpnState
import com.claustrophobDev.vinyl.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

enum class Tab { HOME, SERVERS, ROUTING, SETTINGS }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as VinylApp
    private val storage = app.storage

    val state: StateFlow<AppState> = storage.state
    val loaded: StateFlow<Boolean> = storage.loaded
    val pings: StateFlow<PingMap> = app.pings
    val appsRepo = app.appsRepo

    val status: StateFlow<VpnStatus> = VpnState.status
    val error: StateFlow<String?> = VpnState.error
    val connectedAt: StateFlow<Long> = VpnState.connectedAt
    val connectedServer: StateFlow<String?> = VpnState.serverName
    val traffic: StateFlow<Traffic> = VpnState.traffic
    val logs = VpnLog.lines

    val tab = MutableStateFlow(Tab.HOME)
    val showAddSheet = MutableStateFlow(false)

    val pinging = MutableStateFlow(false)
    val refreshing = MutableStateFlow<Set<String>>(emptySet())

    // поменяли маршруты или настройки пока впн включен, нужно переподключиться
    val needReconnect = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    init {
        viewModelScope.launch {
            VpnState.status.collect {
                if (it != VpnStatus.CONNECTED) needReconnect.value = false
            }
        }
        viewModelScope.launch {
            val st = storage.get()
            val autoConnect = st.settings.autoConnect && VpnState.status.value == VpnStatus.IDLE &&
                hasServers() && VpnService.prepare(app) == null
            if (autoConnect) {
                startVpn()
            } else if (app.pings.value.isEmpty() && st.servers.isNotEmpty()) {
                pingAll()
            }
        }
    }

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    fun hasServers() = state.value.servers.any { it.unsupported == null }

    fun noServers() {
        message("Сначала добавьте сервер")
        tab.value = Tab.SERVERS
        showAddSheet.value = true
    }

    fun startVpn() {
        needReconnect.value = false
        VinylVpnService.start(app)
    }

    fun stopVpn() {
        VinylVpnService.stop(app)
    }

    fun select(id: String) {
        if (state.value.selectedId == id) return
        storage.update { it.copy(selectedId = id) }
        // если уже подключены, сразу переключаемся на новый сервер
        if (VpnState.status.value == VpnStatus.CONNECTED) startVpn()
    }

    fun pingAll() {
        if (pinging.value) return
        pinging.value = true
        viewModelScope.launch {
            try {
                app.pings.value = emptyMap()
                app.pingServers(state.value.servers)
            } finally {
                pinging.value = false
            }
        }
    }

    // текст из буфера или из поля ввода, там могут быть ключи и ссылки на подписки вперемешку
    fun addFromText(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val urls = text.lines().map { it.trim() }.filter {
                it.startsWith("https://", true) || it.startsWith("http://", true)
            }
            val servers = withContext(Dispatchers.Default) { LinkParser.parseSubscription(text, null) }

            if (servers.isNotEmpty()) {
                var added = 0
                storage.update { s ->
                    val newOnes = servers.filter { srv -> s.servers.none { it.id == srv.id } }
                    added = newOnes.size
                    s.copy(servers = s.servers + newOnes)
                }
                if (added > 0) {
                    message("Добавлено: $added ${serversWord(added)}")
                } else {
                    message("Эти серверы уже есть в списке")
                }
                if (servers.size == 1 && added == 1) select(servers[0].id)
            }

            for (url in urls) {
                val existing = state.value.subscriptions.find { it.url == url }
                launch { loadSubscription(url, existing) }
            }

            if (servers.isEmpty() && urls.isEmpty()) {
                message("Не нашел ключей. Поддерживаются vless, vmess, trojan, ss, hy2, tuic и ссылки на подписки")
            }
        }
    }

    fun refreshAll() {
        val subs = state.value.subscriptions
        if (subs.isEmpty()) {
            message("Подписок пока нет")
            return
        }
        for (sub in subs) {
            viewModelScope.launch { loadSubscription(sub.url, sub) }
        }
    }

    fun refresh(sub: Subscription) {
        viewModelScope.launch { loadSubscription(sub.url, sub) }
    }

    private suspend fun loadSubscription(url: String, existing: Subscription?) {
        val id = existing?.id ?: Utils.sha1Short("sub|$url")
        if (id in refreshing.value) return
        refreshing.update { it + id }
        try {
            val result = SubLoader.load(url, id)
            val name = result.title ?: existing?.name ?: hostOf(url)
            val sub = Subscription(
                id, name, url, System.currentTimeMillis(),
                result.upload, result.download, result.total, result.expire
            )

            storage.update { s ->
                val subs = if (s.subscriptions.any { it.id == id }) {
                    s.subscriptions.map { if (it.id == id) sub else it }
                } else {
                    s.subscriptions + sub
                }
                val servers = s.servers.filter { it.subscriptionId != id } + result.servers
                // выбранный сервер мог пропасть из подписки после обновления
                val keepSelected = s.selectedId == AUTO_SERVER_ID || servers.any { it.id == s.selectedId }
                s.copy(
                    subscriptions = subs,
                    servers = servers,
                    selectedId = if (keepSelected) s.selectedId else AUTO_SERVER_ID
                )
            }

            val count = result.servers.size
            if (existing == null) {
                message("$name: $count ${serversWord(count)}")
            } else {
                message("Подписка $name обновлена")
            }
            app.pingServers(result.servers)
        } catch (e: Exception) {
            VpnLog.error("Подписка $url: ${e.message}")
            message("Не удалось загрузить подписку: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            refreshing.update { it - id }
        }
    }

    private fun hostOf(url: String): String {
        return try {
            URL(url).host.removePrefix("www.")
        } catch (e: Exception) {
            "Подписка"
        }
    }

    fun deleteServer(id: String) {
        storage.update { s ->
            s.copy(
                servers = s.servers.filter { it.id != id },
                selectedId = if (s.selectedId == id) AUTO_SERVER_ID else s.selectedId
            )
        }
    }

    fun deleteSubscription(id: String) {
        storage.update { s ->
            val servers = s.servers.filter { it.subscriptionId != id }
            s.copy(
                subscriptions = s.subscriptions.filter { it.id != id },
                servers = servers,
                selectedId = if (servers.any { it.id == s.selectedId }) s.selectedId else AUTO_SERVER_ID
            )
        }
    }

    // ---- маршруты ----

    private fun changeRouting(block: (Routing) -> Routing) {
        storage.update { it.copy(routing = block(it.routing)) }
        if (VpnState.status.value == VpnStatus.CONNECTED) needReconnect.value = true
    }

    fun setAppMode(mode: AppMode) = changeRouting { it.copy(appMode = mode) }

    fun toggleApp(pkg: String) = changeRouting { r ->
        r.copy(apps = if (pkg in r.apps) r.apps - pkg else r.apps + pkg)
    }

    fun setBypassBanks(on: Boolean) = changeRouting { it.copy(bypassBanks = on) }

    fun setDirectRuSites(on: Boolean) = changeRouting { it.copy(directRuSites = on) }

    fun addDomain(input: String): Boolean {
        val domain = SingBoxConfig.cleanDomain(input)
        if (domain == null) {
            message("Это не похоже на домен")
            return false
        }
        if (domain in state.value.routing.directDomains) return true
        changeRouting { it.copy(directDomains = it.directDomains + domain) }
        return true
    }

    fun removeDomain(domain: String) = changeRouting { it.copy(directDomains = it.directDomains - domain) }

    // ---- настройки ----

    fun updateSettings(block: (Settings) -> Settings) {
        val before = state.value.settings
        storage.update { it.copy(settings = block(it.settings)) }
        val after = state.value.settings
        // автоподключение на туннель не влияет, остальное требует переподключения
        val changed = before.ipv6 != after.ipv6 || before.dns != after.dns
        if (changed && VpnState.status.value == VpnStatus.CONNECTED) needReconnect.value = true
    }

    fun clearLogs() = VpnLog.clear()
}
