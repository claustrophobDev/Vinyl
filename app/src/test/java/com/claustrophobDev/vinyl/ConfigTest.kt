package com.claustrophobDev.vinyl

import com.claustrophobDev.vinyl.core.LinkParser
import com.claustrophobDev.vinyl.core.SingBoxConfig
import com.claustrophobDev.vinyl.data.AppMode
import com.claustrophobDev.vinyl.data.AppState
import com.claustrophobDev.vinyl.data.RemoteDns
import com.claustrophobDev.vinyl.data.Routing
import com.claustrophobDev.vinyl.data.Settings
import com.claustrophobDev.vinyl.data.StateSerializer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {

    private val pkg = "com.claustrophobDev.vinyl"

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    @Test
    fun newConfigFormat() {
        val proxy = LinkParser.toProxy("trojan://pw@t.example.com:443#T")
        val routing = Routing(directRuSites = true, directDomains = listOf("https://Kinopoisk.ru/path"))
        val config = JSONObject(SingBoxConfig.build(proxy, routing, Settings(dns = RemoteDns.GOOGLE), pkg))

        // dns только в новом формате
        val dns = config.getJSONObject("dns")
        for (server in dns.getJSONArray("servers").objects()) {
            assertTrue(server.has("type"))
            assertFalse(server.has("address"))
        }
        val remote = dns.getJSONArray("servers").objects().first { it.getString("tag") == "dns-remote" }
        assertEquals("8.8.8.8", remote.getString("server"))
        assertEquals("ipv4_only", dns.getString("strategy"))

        // outbound dns и block удалены в 1.13
        val types = config.getJSONArray("outbounds").objects().map { it.getString("type") }
        assertFalse("dns" in types)
        assertFalse("block" in types)

        val tun = config.getJSONArray("inbounds").getJSONObject(0)
        assertFalse(tun.has("sniff"))
        val excluded = tun.getJSONArray("exclude_package").strings()
        // себя исключать нельзя, иначе tcp виснет
        assertFalse(pkg in excluded)
        assertTrue("ru.sberbankmobile" in excluded)

        val route = config.getJSONObject("route")
        assertEquals("dns-local", route.getString("default_domain_resolver"))
        val rules = route.getJSONArray("rules").objects()
        val actions = rules.mapNotNull { it.optString("action").ifEmpty { null } }
        assertTrue("sniff" in actions)
        assertTrue("hijack-dns" in actions)
        assertTrue(rules.any { it.optInt("ip_version") == 6 && it.optString("action") == "reject" })

        val domains = rules.first { it.has("domain_suffix") }.getJSONArray("domain_suffix").strings()
        assertTrue("kinopoisk.ru" in domains)
        assertTrue("ru" in domains)
    }

    @Test
    fun ipv6On() {
        val proxy = LinkParser.toProxy("trojan://pw@t.example.com:443#T")
        val config = JSONObject(SingBoxConfig.build(proxy, Routing(), Settings(ipv6 = true), pkg))
        val rules = config.getJSONObject("route").getJSONArray("rules").objects()
        assertFalse(rules.any { it.optInt("ip_version") == 6 })
    }

    @Test
    fun wireguardGoesToEndpoints() {
        val key = "0123456789abcdef0123456789abcdef"
        val b64 = java.util.Base64.getEncoder().encodeToString(key.toByteArray())
        val link = "wireguard://$b64@1.2.3.4:51820?publickey=$b64&address=10.0.0.2/32&reserved=1,2,3&mtu=1408#WG"
        val proxy = LinkParser.toProxy(link)
        assertTrue(proxy.isEndpoint)

        val config = JSONObject(SingBoxConfig.build(proxy, Routing(), Settings(), pkg))
        // wireguard лежит в endpoints, а не в outbounds
        val endpoints = config.getJSONArray("endpoints").objects()
        assertEquals(1, endpoints.size)
        val ep = endpoints[0]
        assertEquals("wireguard", ep.getString("type"))
        assertEquals("proxy", ep.getString("tag"))
        val peer = ep.getJSONArray("peers").getJSONObject(0)
        assertEquals("1.2.3.4", peer.getString("address"))
        assertEquals(51820, peer.getInt("port"))
        assertEquals(listOf(1, 2, 3), peer.getJSONArray("reserved").let { (0 until it.length()).map { i -> it.getInt(i) } })

        val outTypes = config.getJSONArray("outbounds").objects().map { it.getString("type") }
        assertEquals(listOf("direct"), outTypes)
        assertEquals("proxy", config.getJSONObject("route").getString("final"))
    }

    @Test
    fun onlyMode() {
        val routing = Routing(appMode = AppMode.ONLY, apps = setOf("org.telegram.messenger", pkg))
        val (include, exclude) = SingBoxConfig.packages(routing, pkg)
        assertEquals(listOf("org.telegram.messenger", pkg), include)
        assertTrue(exclude.isEmpty())
    }

    @Test
    fun onlyModeNothingSelected() {
        val (include, exclude) = SingBoxConfig.packages(Routing(appMode = AppMode.ONLY, bypassBanks = false), pkg)
        assertTrue(include.isEmpty())
        assertTrue(exclude.isEmpty())
    }

    @Test
    fun exceptMode() {
        val routing = Routing(appMode = AppMode.EXCEPT, apps = setOf("com.bank", pkg), bypassBanks = false)
        val (include, exclude) = SingBoxConfig.packages(routing, pkg)
        assertTrue(include.isEmpty())
        assertEquals(listOf("com.bank"), exclude)
    }

    @Test
    fun saveAndLoadState() {
        val server = LinkParser.toServer("vless://id@a.com:443?security=reality&pbk=K#🇫🇮 Helsinki", "s1")!!
        val state = AppState(
            servers = listOf(server),
            selectedId = server.id,
            routing = Routing(appMode = AppMode.EXCEPT, apps = setOf("a.b"), directDomains = listOf("x.ru")),
            settings = Settings(ipv6 = true, dns = RemoteDns.QUAD9, autoConnect = true)
        )
        assertEquals(state, StateSerializer.fromJson(StateSerializer.toJson(state)))
    }
}
