package com.claustrophobDev.vinyl.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.claustrophobDev.vinyl.MainActivity
import com.claustrophobDev.vinyl.R
import com.claustrophobDev.vinyl.VinylApp
import com.claustrophobDev.vinyl.core.LinkParser
import com.claustrophobDev.vinyl.core.SingBoxConfig
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.OutboundGroupItemIterator
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.RoutePrefix
import io.nekohasekai.libbox.RoutePrefixIterator
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import io.nekohasekai.libbox.NetworkInterface as BoxInterface

class VinylVpnService : VpnService(), PlatformInterface, CommandServerHandler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // старт и стоп строго по очереди, чтобы при быстрых нажатиях не поднять ядро два раза
    private val queue = Dispatchers.IO.limitedParallelism(1)
    // номер попытки подключения, чтобы старый запуск не перетер состояние нового
    private val attempt = AtomicInteger()

    private lateinit var watcher: NetworkWatcher
    private lateinit var systemDns: SystemDns

    private var core: CommandServer? = null
    private var statsClient: CommandClient? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var statsJob: Job? = null
    private var trafficJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        watcher = NetworkWatcher(this)
        systemDns = SystemDns(watcher)
        val channel = NotificationChannel(CHANNEL_ID, "VPN-подключение", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
        } else {
            // без action нас запускает сама система (постоянный впн)
            showNotification("Подключение...")
            startVpn()
        }
        return START_NOT_STICKY
    }

    override fun onRevoke() {
        VpnLog.warn("Система отключила VPN (включили другой VPN или забрали разрешение)")
        stopVpn()
    }

    override fun onDestroy() {
        attempt.incrementAndGet()
        closeCore()
        if (VpnState.status.value != VpnStatus.ERROR) resetState()
        watcher.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun startVpn() {
        val id = attempt.incrementAndGet()
        VpnState.error.value = null
        VpnState.status.value = VpnStatus.CONNECTING

        scope.launch(queue) {
            closeCore()
            try {
                val app = VinylApp.instance
                val state = app.storage.get()
                val srv = app.pickServer(state)
                    ?: throw IllegalStateException("Сначала добавьте сервер (ключ или ссылку на подписку)")
                val config = SingBoxConfig.build(LinkParser.toProxy(srv.link), state.routing, state.settings, packageName)
                if (attempt.get() != id) return@launch

                setupLibbox()
                watcher.start()
                val server = Libbox.newCommandServer(this@VinylVpnService, this@VinylVpnService)
                core = server
                server.start()
                server.startOrReloadService(config, OverrideOptions())

                if (attempt.get() != id) return@launch
                VpnState.serverName.value = srv.name
                VpnState.traffic.value = Traffic()
                VpnState.connectedAt.value = SystemClock.elapsedRealtime()
                VpnState.status.value = VpnStatus.CONNECTED
                updateNotification(srv.flag + " " + srv.name)
                VinylTileService.refresh(this@VinylVpnService)
                VpnLog.info("Подключено: ${srv.name} (${srv.protocol.label})")
                startStats()
            } catch (e: Throwable) {
                val msg = if (e.message.isNullOrBlank()) e.javaClass.simpleName else e.message!!
                VpnLog.error("Ошибка подключения: $msg")
                closeCore()
                if (attempt.get() != id) return@launch
                resetState()
                VpnState.error.value = msg
                VpnState.status.value = VpnStatus.ERROR
                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun stopVpn() {
        attempt.incrementAndGet()
        val status = VpnState.status.value
        if (status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING) {
            VpnState.status.value = VpnStatus.STOPPING
        }
        scope.launch(queue) {
            val wasRunning = core != null
            closeCore()
            resetState()
            VpnState.status.value = VpnStatus.IDLE
            if (wasRunning) VpnLog.info("Отключено")
            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun resetState() {
        VpnState.traffic.value = Traffic()
        VpnState.connectedAt.value = 0L
        VpnState.serverName.value = null
        if (VpnState.status.value != VpnStatus.ERROR) VpnState.status.value = VpnStatus.IDLE
        VinylTileService.refresh(this)
    }

    @Synchronized
    private fun closeCore() {
        statsJob?.cancel()
        statsJob = null
        trafficJob?.cancel()
        trafficJob = null

        try {
            statsClient?.disconnect()
        } catch (e: Exception) {
        }
        statsClient = null

        val server = core
        if (server != null) {
            try { server.closeService() } catch (e: Exception) {}
            try { server.close() } catch (e: Exception) {}
        }
        core = null

        try {
            tunFd?.close()
        } catch (e: Exception) {
        }
        tunFd = null
        watcher.stop()
    }

    // Libbox.setup можно звать только один раз на процесс
    private fun setupLibbox() = synchronized(setupLock) {
        if (libboxReady) return@synchronized
        val base = File(filesDir, "core").apply { mkdirs() }
        val temp = File(cacheDir, "core").apply { mkdirs() }
        val options = SetupOptions()
        options.basePath = base.path
        options.workingPath = base.path
        options.tempPath = temp.path
        options.fixAndroidStack = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        options.logMaxLines = 300L
        options.appVersion = packageManager.getPackageInfo(packageName, 0).longVersionCode.toString()
        options.appMarketingVersion = "Vinyl"
        Libbox.setup(options)
        libboxReady = true
        VpnLog.info("Ядро sing-box ${Libbox.version()}")
    }

    // скорость и логи берем у самого ядра через command client
    private fun startStats() {
        val options = CommandClientOptions()
        options.addCommand(Libbox.CommandStatus)
        options.addCommand(Libbox.CommandLog)
        options.statusInterval = 1_000_000_000L // в наносекундах, раз в секунду
        val client = Libbox.newCommandClient(statsHandler, options)

        statsJob = scope.launch {
            // сервер команд поднимается не сразу, пробуем несколько раз
            for (i in 1..10) {
                delay(100L + i * 50L)
                val connected = try {
                    client.connect()
                    true
                } catch (e: Exception) {
                    false
                }
                if (connected) {
                    if (isActive) {
                        statsClient = client
                    } else {
                        try { client.disconnect() } catch (e: Exception) {}
                    }
                    return@launch
                }
            }
            countTrafficManually()
        }
    }

    // запасной вариант если ядро статистику не отдает: считаем байты сокетов нашего uid
    private fun countTrafficManually() {
        if (trafficJob != null) return
        trafficJob = scope.launch {
            val uid = Process.myUid()
            val startRx = TrafficStats.getUidRxBytes(uid)
            val startTx = TrafficStats.getUidTxBytes(uid)
            var lastRx = startRx
            var lastTx = startTx
            while (isActive) {
                delay(1000)
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                VpnState.traffic.value = Traffic(tx - lastTx, rx - lastRx, tx - startTx, rx - startRx)
                lastRx = rx
                lastTx = tx
            }
        }
    }

    private val statsHandler = object : CommandClientHandler {
        override fun writeStatus(message: StatusMessage?) {
            if (message == null) return
            if (VpnState.status.value != VpnStatus.CONNECTED) return
            if (message.trafficAvailable) {
                VpnState.traffic.value = Traffic(message.uplink, message.downlink, message.uplinkTotal, message.downlinkTotal)
            } else {
                countTrafficManually()
            }
        }

        override fun writeLogs(messageList: LogIterator?) {
            if (messageList == null) return
            // с подпиской на логи ядро шлет debug по каждому соединению, оставляем только warn и error
            while (messageList.hasNext()) {
                val entry = messageList.next() ?: continue
                val level = when {
                    entry.level <= 2 -> LogLevel.ERROR
                    entry.level == 3 -> LogLevel.WARN
                    else -> continue
                }
                VpnLog.add(level, ANSI_COLORS.replace(entry.message ?: "", ""))
            }
        }

        override fun clearLogs() {}
        override fun connected() {}
        override fun disconnected(message: String?) {}
        override fun initializeClashMode(modeList: StringIterator?, currentMode: String?) {}
        override fun setDefaultLogLevel(level: Int) {}
        override fun updateClashMode(newMode: String?) {}
        override fun writeConnectionEvents(events: ConnectionEvents?) {}
        override fun writeGroups(message: OutboundGroupIterator?) {}
        override fun writeOutbounds(message: OutboundGroupItemIterator?) {}
    }

    // ---- PlatformInterface ----

    override fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) error("Нет разрешения на VPN, откройте Vinyl и подключитесь заново")

        val builder = Builder().setSession("Vinyl").setMtu(options.getMTU())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val hasV4 = addPrefixes(options.getInet4Address()) { builder.addAddress(it.address(), it.prefix()) } > 0
        val hasV6 = addPrefixes(options.getInet6Address()) { builder.addAddress(it.address(), it.prefix()) } > 0

        if (options.getAutoRoute()) {
            if (options.getDNSMode()?.value != Libbox.DNSModeDisabled) {
                val dnsServers = options.getDNSServerAddress()
                while (dnsServers.hasNext()) {
                    dnsServers.next()?.let { builder.addDnsServer(it) }
                }
            }

            val v4 = addPrefixes(options.getInet4RouteRange()) { builder.addRoute(it.address(), it.prefix()) }
            if (v4 == 0 && hasV4) builder.addRoute("0.0.0.0", 0)
            val v6 = addPrefixes(options.getInet6RouteRange()) { builder.addRoute(it.address(), it.prefix()) }
            if (v6 == 0 && hasV6) builder.addRoute("::", 0)

            // allowed и disallowed вместе андроид не дает использовать
            val include = options.getIncludePackage()
            if (include.hasNext()) {
                while (include.hasNext()) {
                    val pkg = include.next() ?: continue
                    try { builder.addAllowedApplication(pkg) } catch (e: PackageManager.NameNotFoundException) {}
                }
            } else {
                val exclude = options.getExcludePackage()
                while (exclude.hasNext()) {
                    val pkg = exclude.next() ?: continue
                    try { builder.addDisallowedApplication(pkg) } catch (e: PackageManager.NameNotFoundException) {}
                }
            }
        }

        val fd = builder.establish() ?: error("Android не создал VPN-интерфейс")
        try {
            tunFd?.close()
        } catch (e: Exception) {
        }
        tunFd = fd
        return fd.fd
    }

    private inline fun addPrefixes(list: RoutePrefixIterator, add: (RoutePrefix) -> Unit): Int {
        var count = 0
        while (list.hasNext()) {
            val prefix = list.next() ?: continue
            add(prefix)
            count++
        }
        return count
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        protect(fd)
    }

    override fun usePlatformAutoDetectInterfaceControl() = true

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        watcher.setListener(listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener?) {
        watcher.setListener(null)
    }

    override fun localDNSTransport(): LocalDNSTransport = systemDns

    override fun getInterfaces(): NetworkInterfaceIterator {
        val cm = getSystemService(ConnectivityManager::class.java)
        val sysInterfaces = try {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
        } catch (e: Exception) {
            emptyList()
        }

        val result = ArrayList<BoxInterface>()
        @Suppress("DEPRECATION")
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val link = cm.getLinkProperties(network) ?: continue
            val name = link.interfaceName ?: continue
            val nif = sysInterfaces.find { it.name == name } ?: continue

            val item = BoxInterface()
            item.name = name
            item.index = nif.index
            try { item.mtu = nif.mtu } catch (e: Exception) {}
            item.type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                else -> Libbox.InterfaceTypeOther
            }
            val addresses = nif.interfaceAddresses.mapNotNull { a ->
                a.address?.hostAddress?.substringBefore('%')?.let { "$it/${a.networkPrefixLength}" }
            }
            item.addresses = StringList(addresses)
            item.dnsServer = StringList(link.dnsServers.mapNotNull { it.hostAddress })

            var ifFlags = 0
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) ifFlags = ifFlags or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
            if (nif.isLoopback) ifFlags = ifFlags or OsConstants.IFF_LOOPBACK
            if (nif.isPointToPoint) ifFlags = ifFlags or OsConstants.IFF_POINTOPOINT
            if (nif.supportsMulticast()) ifFlags = ifFlags or OsConstants.IFF_MULTICAST
            item.flags = ifFlags
            item.metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            result.add(item)
        }
        return InterfaceList(result)
    }

    override fun useProcFS() = false
    override fun includeAllNetworks() = false
    override fun underNetworkExtension() = false
    override fun usePlatformBridge() = false
    override fun usePlatformShell() = false
    override fun clearDNSCache() {}
    override fun registerMyInterface(name: String?) {}
    override fun startNeighborMonitor(listener: NeighborUpdateListener?) {}
    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {}
    override fun cancelNotification(identifier: String?, typeID: Int) {}
    override fun sendNotification(notification: Notification?) {}
    override fun readWIFIState(): WIFIState? = null
    override fun tailscaleHostname() = "vinyl"

    // все что ниже нам не нужно (ssh, tailscale и тд)
    override fun checkPlatformShell() = throw UnsupportedOperationException("shell")
    override fun lookupSFTPServer(): String = throw UnsupportedOperationException("sftp")
    override fun readSystemSSHHostKey(): String = throw UnsupportedOperationException("ssh")
    override fun openShellSession(
        user: PlatformUser?, command: String?, environ: StringIterator?, term: String?, rows: Int, cols: Int
    ): ShellSession = throw UnsupportedOperationException("shell")
    override fun createBridge(options: BridgeOptions?): BridgeSession = throw UnsupportedOperationException("bridge")
    override fun findConnectionOwner(
        ipProtocol: Int, sourceAddress: String?, sourcePort: Int, destinationAddress: String?, destinationPort: Int
    ): ConnectionOwner = throw UnsupportedOperationException("process lookup")

    override fun lookupUser(username: String?): PlatformUser {
        val user = PlatformUser()
        user.username = username ?: ""
        user.uid = Process.myUid()
        user.gid = Process.myUid()
        user.homeDir = filesDir.absolutePath
        return user
    }

    // ---- CommandServerHandler ----

    override fun serviceStop() = stopVpn()
    override fun serviceReload() = startVpn()

    override fun getSystemProxyStatus(): SystemProxyStatus {
        val st = SystemProxyStatus()
        st.available = false
        st.enabled = false
        return st
    }

    override fun setSystemProxyEnabled(enabled: Boolean) {}
    override fun triggerNativeCrash() {}
    override fun connectSSHAgent() = -1

    override fun writeDebugMessage(message: String?) {
        if (message != null) VpnLog.info(message)
    }

    // ---- уведомление ----

    private fun buildNotification(text: String): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, VinylVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vinyl)
            .setColor(0xFF8B6CFF.toInt())
            .setContentTitle("Vinyl")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp)
            .addAction(0, "Отключить", stop)
            .build()
    }

    private fun showNotification(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private class StringList(values: List<String>) : StringIterator {
        private val size = values.size
        private val it = values.iterator()
        override fun hasNext() = it.hasNext()
        override fun len() = size
        override fun next(): String = it.next()
    }

    private class InterfaceList(values: List<BoxInterface>) : NetworkInterfaceIterator {
        private val it = values.iterator()
        override fun hasNext() = it.hasNext()
        override fun next(): BoxInterface = it.next()
    }

    companion object {
        const val ACTION_START = "com.claustrophobDev.vinyl.action.START"
        const val ACTION_STOP = "com.claustrophobDev.vinyl.action.STOP"

        private const val CHANNEL_ID = "vinyl_vpn"
        private const val NOTIFICATION_ID = 33

        // ядро красит логи ansi кодами, в приложении они не нужны
        private val ANSI_COLORS = Regex(Char(27) + "\\[[0-9;]*m")

        private val setupLock = Any()
        @Volatile
        private var libboxReady = false

        fun start(context: Context) {
            val intent = Intent(context, VinylVpnService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, VinylVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
