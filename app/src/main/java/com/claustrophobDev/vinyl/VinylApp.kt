package com.claustrophobDev.vinyl

import android.app.Application
import android.net.ConnectivityManager
import com.claustrophobDev.vinyl.core.NotSupportedException
import com.claustrophobDev.vinyl.core.Ping
import com.claustrophobDev.vinyl.data.AUTO_SERVER_ID
import com.claustrophobDev.vinyl.data.AppState
import com.claustrophobDev.vinyl.data.AppsRepo
import com.claustrophobDev.vinyl.data.PingMap
import com.claustrophobDev.vinyl.data.Server
import com.claustrophobDev.vinyl.data.Storage
import com.claustrophobDev.vinyl.vpn.realNetwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class VinylApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var storage: Storage
        private set
    lateinit var appsRepo: AppsRepo
        private set

    // пинги на диск не сохраняем, все равно быстро устаревают
    val pings = MutableStateFlow<PingMap>(emptyMap())

    override fun onCreate() {
        super.onCreate()
        instance = this
        storage = Storage(this, scope)
        appsRepo = AppsRepo(this)
        appsRepo.load(scope)
    }

    suspend fun pingServers(list: List<Server>) {
        val cm = getSystemService(ConnectivityManager::class.java)
        Ping.pingAll(list, cm.realNetwork()) { id, ms ->
            pings.update { it + (id to ms) }
        }
    }

    // выбранный сервер, а если стоит автовыбор то самый быстрый
    suspend fun pickServer(state: AppState): Server? {
        if (state.selectedId != AUTO_SERVER_ID) {
            val selected = state.servers.find { it.id == state.selectedId }
            if (selected != null) {
                if (selected.unsupported != null) throw NotSupportedException(selected.unsupported)
                return selected
            }
        }

        val list = state.servers.filter { it.unsupported == null }
        if (list.isEmpty()) return null

        var best = fastest(list)
        if (best == null) {
            // еще ни разу не пинговали, делаем это сейчас
            pingServers(list)
            best = fastest(list)
        }
        return best ?: list.first()
    }

    private fun fastest(list: List<Server>): Server? {
        var best: Server? = null
        var bestMs = Int.MAX_VALUE
        for (s in list) {
            val ms = pings.value[s.id] ?: continue
            if (ms in 1 until bestMs) {
                best = s
                bestMs = ms
            }
        }
        return best
    }

    companion object {
        lateinit var instance: VinylApp
            private set
    }
}
