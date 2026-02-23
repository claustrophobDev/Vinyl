package com.claustrophobDev.vinyl

import com.claustrophobDev.vinyl.core.LinkParser
import com.claustrophobDev.vinyl.core.NotSupportedException
import com.claustrophobDev.vinyl.data.Protocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class ParserTest {

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
    private fun b64url(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

    @Test
    fun vlessReality() {
        val link = "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@example.com:443?type=tcp&security=reality" +
            "&pbk=PUBKEY&sid=ab12&sni=www.microsoft.com&fp=chrome&flow=xtls-rprx-vision#🇳🇱 Amsterdam | Fast"
        val server = LinkParser.toServer(link, null)!!
        assertEquals("🇳🇱", server.flag)
        assertEquals("Amsterdam | Fast", server.name)
        assertEquals(Protocol.VLESS, server.protocol)
        assertEquals(443, server.port)

        val out = LinkParser.toOutbound(link)
        assertEquals("vless", out.getString("type"))
        assertEquals("proxy", out.getString("tag"))
        assertEquals("xtls-rprx-vision", out.getString("flow"))

        val tls = out.getJSONObject("tls")
        assertEquals("www.microsoft.com", tls.getString("server_name"))
        assertEquals("PUBKEY", tls.getJSONObject("reality").getString("public_key"))
        assertEquals("ab12", tls.getJSONObject("reality").getString("short_id"))
        assertEquals("chrome", tls.getJSONObject("utls").getString("fingerprint"))
        assertFalse(tls.has("insecure"))
        assertFalse(out.has("transport"))
    }

    @Test
    fun realityWithoutFp() {
        // без fp reality не заведется, должен подставиться chrome
        val out = LinkParser.toOutbound("vless://id@1.2.3.4:443?security=reality&pbk=KEY&sni=a.com#x")
        assertEquals("chrome", out.getJSONObject("tls").getJSONObject("utls").getString("fingerprint"))
    }

    @Test
    fun vlessWsEarlyData() {
        val link = "vless://id@1.2.3.4:8443?type=ws&security=tls&host=cdn.example.com&path=%2Fws%3Fed%3D2048#Germany"
        assertEquals("🇩🇪", LinkParser.toServer(link, "sub")!!.flag)

        val out = LinkParser.toOutbound(link)
        assertEquals("cdn.example.com", out.getJSONObject("tls").getString("server_name"))
        val ws = out.getJSONObject("transport")
        assertEquals("ws", ws.getString("type"))
        assertEquals("/ws", ws.getString("path"))
        assertEquals(2048, ws.getInt("max_early_data"))
        assertEquals("cdn.example.com", ws.getJSONObject("headers").getString("Host"))
    }

    @Test
    fun vmessGrpc() {
        val json = JSONObject()
            .put("v", "2").put("ps", "Finland 1").put("add", "fi.example.com").put("port", "443")
            .put("id", "uuid-1").put("aid", "0").put("scy", "auto").put("net", "grpc").put("type", "none")
            .put("path", "svc").put("tls", "tls").put("sni", "fi.example.com")
        val link = "vmess://" + b64(json.toString())

        val server = LinkParser.toServer(link, null)!!
        assertEquals("Finland 1", server.name)
        assertEquals("fi.example.com", server.host)

        val out = LinkParser.toOutbound(link)
        assertEquals("vmess", out.getString("type"))
        assertEquals("svc", out.getJSONObject("transport").getString("service_name"))
        assertTrue(out.getJSONObject("tls").getBoolean("enabled"))
    }

    @Test
    fun httpUpgrade() {
        val out = LinkParser.toOutbound("vless://id@h.com:80?type=httpupgrade&host=up.example.com&path=/up#x")
        assertEquals("up.example.com", out.getJSONObject("transport").getString("host"))
        assertFalse(out.has("tls"))
    }

    @Test
    fun trojanPassword() {
        // плюс в пароле не должен стать пробелом
        val out = LinkParser.toOutbound("trojan://p%40ss+word@t.example.com:443?sni=t.example.com#Trojan")
        assertEquals("p@ss+word", out.getString("password"))
        assertTrue(out.getJSONObject("tls").getBoolean("enabled"))
    }

    @Test
    fun shadowsocks() {
        val sip002 = LinkParser.toOutbound("ss://${b64url("chacha20-ietf-poly1305:secret")}@1.2.3.4:8388#SS")
        assertEquals("chacha20-ietf-poly1305", sip002.getString("method"))
        assertEquals("secret", sip002.getString("password"))

        val ss2022 = LinkParser.toOutbound("ss://2022-blake3-aes-128-gcm:YWJjZGVmZ2hpamtsbW5vcA%3D%3D@h.com:443#x")
        assertEquals("YWJjZGVmZ2hpamtsbW5vcA==", ss2022.getString("password"))

        val old = LinkParser.toServer("ss://${b64("aes-256-gcm:pw@5.6.7.8:1234")}#Old", null)!!
        assertEquals("5.6.7.8", old.host)
        assertEquals(1234, old.port)
        assertEquals("pw", LinkParser.toOutbound(old.link).getString("password"))
    }

    @Test
    fun hysteria2() {
        val out = LinkParser.toOutbound("hy2://pass@hy.example.com:443?obfs=salamander&obfs-password=ob&insecure=1#HY")
        assertEquals("hysteria2", out.getString("type"))
        assertEquals("pass", out.getString("password"))
        assertEquals("ob", out.getJSONObject("obfs").getString("password"))
        assertTrue(out.getJSONObject("tls").getBoolean("insecure"))
    }

    @Test
    fun tuic() {
        val out = LinkParser.toOutbound("tuic://uuid:secret@t.example.com:443?congestion_control=bbr&alpn=h3#T")
        assertEquals("uuid", out.getString("uuid"))
        assertEquals("secret", out.getString("password"))
        assertEquals("h3", out.getJSONObject("tls").getJSONArray("alpn").getString(0))
    }

    @Test
    fun xhttpNotSupported() {
        val link = "vless://id@x.com:443?type=xhttp&security=tls#X"
        val server = LinkParser.toServer(link, null)
        assertNotNull(server)
        assertNotNull(server!!.unsupported)
        try {
            LinkParser.toOutbound(link)
            fail("должно было упасть")
        } catch (e: NotSupportedException) {
            // ок
        }
    }

    @Test
    fun badLinks() {
        assertNull(LinkParser.toServer("vless://@:443", null))
        assertNull(LinkParser.toServer("https://example.com", null))
    }

    @Test
    fun base64Subscription() {
        val body = b64("vless://id@a.com:443#A\ntrojan://pw@b.com:443#B\nкакой-то мусор")
        val servers = LinkParser.parseSubscription(body, "sub1")
        assertEquals(2, servers.size)
        assertTrue(servers.all { it.subscriptionId == "sub1" })
    }
}
