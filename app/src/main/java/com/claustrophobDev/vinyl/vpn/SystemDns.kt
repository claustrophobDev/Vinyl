package com.claustrophobDev.vinyl.vpn

import android.net.DnsResolver
import android.os.Build
import android.os.CancellationSignal
import android.system.ErrnoException
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.LocalDNSTransport
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// это dns с типом "local" из конфига ядра
// резолвим через системный резолвер настоящей сети, мимо туннеля
class SystemDns(private val watcher: NetworkWatcher) : LocalDNSTransport {

    private val executor = Executors.newCachedThreadPool()

    override fun raw(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @Suppress("DEPRECATION")
    override fun exchange(ctx: ExchangeContext, message: ByteArray) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ctx.errorCode(SERVFAIL)
            return
        }
        val latch = CountDownLatch(1)
        val cancel = CancellationSignal()
        ctx.onCancel {
            cancel.cancel()
            latch.countDown()
        }
        val callback = object : DnsResolver.Callback<ByteArray> {
            override fun onAnswer(answer: ByteArray, rcode: Int) {
                if (rcode == 0) ctx.rawSuccess(answer) else ctx.errorCode(rcode)
                latch.countDown()
            }

            override fun onError(error: DnsResolver.DnsException) {
                val cause = error.cause
                if (cause is ErrnoException) ctx.errnoCode(cause.errno) else ctx.errorCode(SERVFAIL)
                latch.countDown()
            }
        }
        DnsResolver.getInstance().rawQuery(watcher.current, message, DnsResolver.FLAG_NO_RETRY, executor, cancel, callback)
        if (!latch.await(15, TimeUnit.SECONDS)) ctx.errorCode(SERVFAIL)
    }

    override fun lookup(ctx: ExchangeContext, network: String, domain: String) {
        try {
            val all = watcher.current?.getAllByName(domain) ?: InetAddress.getAllByName(domain)
            val filtered = when (network) {
                "ip4" -> all.filterIsInstance<Inet4Address>()
                "ip6" -> all.filterIsInstance<Inet6Address>()
                else -> all.toList()
            }
            val addresses = filtered.mapNotNull { it.hostAddress }
            if (addresses.isEmpty()) ctx.errorCode(NXDOMAIN) else ctx.success(addresses.joinToString("\n"))
        } catch (e: UnknownHostException) {
            ctx.errorCode(NXDOMAIN)
        }
    }

    private companion object {
        const val SERVFAIL = 2
        const val NXDOMAIN = 3
    }
}
