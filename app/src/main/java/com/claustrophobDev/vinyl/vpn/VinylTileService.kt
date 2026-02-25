package com.claustrophobDev.vinyl.vpn

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.claustrophobDev.vinyl.MainActivity
import com.claustrophobDev.vinyl.VinylApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// плитка в шторке, включает/выключает vpn одним тапом
class VinylTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var watchJob: Job? = null

    override fun onStartListening() {
        render()
        // пока плитка на экране, следим за статусом и обновляем ее
        watchJob = scope.launch {
            VpnState.status.collect { render() }
        }
    }

    override fun onStopListening() {
        watchJob?.cancel()
        watchJob = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        when (VpnState.status.value) {
            VpnStatus.CONNECTED, VpnStatus.CONNECTING -> VinylVpnService.stop(this)
            VpnStatus.STOPPING -> {}
            VpnStatus.IDLE, VpnStatus.ERROR -> {
                val noServers = !VinylApp.instance.storage.state.value.servers.any { it.unsupported == null }
                // если нет серверов или андроид еще не спросил разрешение на vpn, открываем приложение
                if (noServers || VpnService.prepare(this) != null) {
                    openApp()
                } else {
                    VinylVpnService.start(this)
                }
            }
        }
        render()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun render() {
        val tile = qsTile ?: return
        val status = VpnState.status.value
        tile.state = if (status == VpnStatus.CONNECTED) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Vinyl"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (status) {
                VpnStatus.CONNECTED -> VpnState.serverName.value ?: "Подключено"
                VpnStatus.CONNECTING -> "Подключение"
                VpnStatus.STOPPING -> "Отключение"
                VpnStatus.ERROR -> "Ошибка"
                VpnStatus.IDLE -> "Отключено"
            }
        }
        tile.updateTile()
    }

    companion object {
        // попросить систему обновить плитку, вызываем из сервиса когда меняется статус
        fun refresh(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, VinylTileService::class.java))
            } catch (e: Exception) {
            }
        }
    }
}
