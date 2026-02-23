package com.claustrophobDev.vinyl

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.claustrophobDev.vinyl.core.LinkParser
import com.claustrophobDev.vinyl.core.SingBoxConfig
import com.claustrophobDev.vinyl.data.AppMode
import com.claustrophobDev.vinyl.data.RemoteDns
import com.claustrophobDev.vinyl.data.Routing
import com.claustrophobDev.vinyl.data.Settings
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import org.json.JSONObject
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64

// гоняем конфиги через проверку настоящего ядра (Libbox.checkConfig), туннель не поднимается
// запускать на телефоне или эмуляторе
@RunWith(AndroidJUnit4::class)
class SingBoxCheckTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

    private val links = listOf(
        "vless://$uuid@example.com:443?type=tcp&security=reality&pbk=Z84J2IelR9ch3k8VtlVhhs5ycBUlXA7wHBWcBrjqnAw" +
            "&sid=6ba85179e30d4fc2&sni=www.microsoft.com&fp=chrome&flow=xtls-rprx-vision#Reality",
        "vless://$uuid@1.2.3.4:8443?type=ws&security=tls&host=cdn.example.com&path=%2Fws%3Fed%3D2048&alpn=h2,http/1.1#WS",
        "vless://$uuid@h.example.com:80?type=httpupgrade&host=up.example.com&path=/up#HttpUpgrade",
        "vless://$uuid@h.example.com:443?type=grpc&serviceName=svc&security=tls&fp=firefox#gRPC",
        "vmess://" + b64(
            JSONObject().put("v", "2").put("ps", "VMess").put("add", "vm.example.com").put("port", "443")
                .put("id", uuid).put("aid", "0").put("scy", "auto").put("net", "ws").put("type", "none")
                .put("host", "vm.example.com").put("path", "/v").put("tls", "tls").toString()
        ),
        "trojan://password@t.example.com:443?sni=t.example.com#Trojan",
        "ss://" + b64("chacha20-ietf-poly1305:secret") + "@1.2.3.4:8388#SS",
        "ss://2022-blake3-aes-128-gcm:YWJjZGVmZ2hpamtsbW5vcA%3D%3D@h.example.com:443#SS2022",
        "hy2://pass@hy.example.com:443?obfs=salamander&obfs-password=ob&sni=hy.example.com#Hysteria2",
        "hy2://pass@hy.example.com:443?mport=20000-30000&insecure=1#Hysteria2Hop",
        "tuic://$uuid:secret@t.example.com:443?congestion_control=bbr&alpn=h3#TUIC"
    )

    private val routings = listOf(
        Routing(),
        Routing(appMode = AppMode.EXCEPT, apps = setOf("org.telegram.messenger"), directRuSites = true, directDomains = listOf("kinopoisk.ru")),
        Routing(appMode = AppMode.ONLY, apps = setOf("org.telegram.messenger"), bypassBanks = false)
    )

    @Test
    fun allConfigsAreValid() {
        val errors = ArrayList<String>()
        for (link in links) {
            for (routing in routings) {
                for (settings in listOf(Settings(), Settings(ipv6 = true, dns = RemoteDns.QUAD9))) {
                    val config = SingBoxConfig.build(LinkParser.toOutbound(link), routing, settings, context.packageName)
                    try {
                        Libbox.checkConfig(config)
                    } catch (e: Exception) {
                        errors.add(link.substringAfterLast('#') + ": " + e.message + "\n" + config)
                    }
                }
            }
        }
        if (errors.isNotEmpty()) throw AssertionError(errors.joinToString("\n\n"))
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() {
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val base = File(ctx.filesDir, "core-test").apply { mkdirs() }
            val temp = File(ctx.cacheDir, "core-test").apply { mkdirs() }
            val options = SetupOptions()
            options.basePath = base.path
            options.workingPath = base.path
            options.tempPath = temp.path
            options.fixAndroidStack = true
            Libbox.setup(options)
        }
    }
}
