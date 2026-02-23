package com.claustrophobDev.vinyl.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import io.nekohasekai.libbox.InterfaceUpdateListener

// следим за настоящей сетью (wifi / мобильный интернет, не впн) и говорим ядру имя интерфейса
// без этого sing-box с auto_detect_interface не знает через что выходить в интернет
class NetworkWatcher(context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val thread = HandlerThread("vinyl-net").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile
    var current: Network? = null
        private set

    @Volatile
    private var listener: InterfaceUpdateListener? = null
    private var registered = false
    private var lastSent: Pair<String, Int>? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            current = network
            sendUpdate()
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            if (network == current) sendUpdate()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (current == null) current = network
            if (network == current) sendUpdate()
        }

        override fun onLost(network: Network) {
            if (network == current) {
                current = cm.realNetwork()
                sendUpdate()
            }
        }
    }

    @Synchronized
    fun start() {
        if (registered) return
        current = cm.realNetwork()
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        // приложение само сидит внутри впн, поэтому "сеть по умолчанию" для него это впн
        // а нам нужна именно настоящая сеть
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cm.registerBestMatchingNetworkCallback(request, callback, handler)
        } else {
            cm.requestNetwork(request, callback, handler)
        }
        registered = true
    }

    @Synchronized
    fun stop() {
        listener = null
        lastSent = null
        if (!registered) return
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
        }
        registered = false
    }

    fun setListener(l: InterfaceUpdateListener?) {
        listener = l
        lastSent = null
        if (l != null) handler.post { sendUpdate() }
    }

    fun close() {
        stop()
        thread.quitSafely()
    }

    private fun sendUpdate() {
        val l = listener ?: return
        val net = current
        if (net == null) {
            lastSent = null
            l.updateDefaultInterface("", -1, false, false)
            return
        }
        val name = cm.getLinkProperties(net)?.interfaceName ?: return
        val caps = cm.getNetworkCapabilities(net)
        val metered = caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        // индекс интерфейса иногда появляется не сразу, ждем немного
        for (i in 0 until 10) {
            val index = try {
                java.net.NetworkInterface.getByName(name)?.index
            } catch (e: Exception) {
                null
            }
            if (index != null) {
                if (lastSent == Pair(name, index)) return
                lastSent = Pair(name, index)
                l.updateDefaultInterface(name, index, metered, false)
                return
            }
            Thread.sleep(100)
        }
    }
}

// лучшая настоящая сеть с интернетом, проверенная и wifi в приоритете
fun ConnectivityManager.realNetwork(): Network? {
    @Suppress("DEPRECATION")
    val all = allNetworks
    var best: Network? = null
    var bestScore = -1
    for (net in all) {
        val caps = getNetworkCapabilities(net) ?: continue
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue
        var score = 0
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) score += 2
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) score += 1
        if (score > bestScore) {
            best = net
            bestScore = score
        }
    }
    return best
}
