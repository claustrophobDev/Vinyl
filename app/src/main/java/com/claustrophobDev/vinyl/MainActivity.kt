package com.claustrophobDev.vinyl

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.claustrophobDev.vinyl.ui.AppRoot
import com.claustrophobDev.vinyl.ui.MainViewModel
import com.claustrophobDev.vinyl.ui.theme.VinylTheme
import com.claustrophobDev.vinyl.vpn.VpnState
import com.claustrophobDev.vinyl.vpn.VpnStatus

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            connect()
        } else {
            vm.message("Без разрешения Android не даст включить VPN")
        }
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            VinylTheme {
                AppRoot(vm, onToggleVpn = { toggleVpn() })
            }
        }
    }

    private fun toggleVpn() {
        when (VpnState.status.value) {
            VpnStatus.CONNECTED, VpnStatus.CONNECTING -> vm.stopVpn()
            VpnStatus.STOPPING -> {}
            VpnStatus.IDLE, VpnStatus.ERROR -> {
                if (!vm.hasServers()) {
                    vm.noServers()
                    return
                }
                val intent = VpnService.prepare(this)
                if (intent != null) vpnPermission.launch(intent) else connect()
            }
        }
    }

    private fun connect() {
        vm.startVpn()
        // разрешение на уведомления спрашиваем тут, а не при запуске вместе с впн
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
