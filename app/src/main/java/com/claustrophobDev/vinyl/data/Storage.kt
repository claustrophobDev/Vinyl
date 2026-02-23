package com.claustrophobDev.vinyl.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// все данные держим в памяти, с диска читаем один раз при старте
// сохраняем в фоне с небольшой задержкой, чтобы не писать файл на каждый тык по свитчу
class Storage(context: Context, private val scope: CoroutineScope) {

    private val file = AtomicFile(File(context.filesDir, "vinyl_state.json"))
    private val ready = CompletableDeferred<Unit>()

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    init {
        scope.launch(Dispatchers.IO) {
            _state.value = try {
                StateSerializer.fromJson(String(file.readFully()))
            } catch (e: Exception) {
                AppState()
            }
            ready.complete(Unit)
            _loaded.value = true
            saveLoop()
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun saveLoop() {
        _state.drop(1).debounce(250).collect { st ->
            val out = file.startWrite()
            try {
                out.write(StateSerializer.toJson(st).toByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
            }
        }
    }

    fun update(block: (AppState) -> AppState) {
        if (ready.isCompleted) {
            _state.update(block)
        } else {
            scope.launch {
                ready.await()
                _state.update(block)
            }
        }
    }

    suspend fun get(): AppState {
        ready.await()
        return _state.value
    }
}

object StateSerializer {

    fun toJson(state: AppState): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("selectedId", state.selectedId)

        val servers = JSONArray()
        for (s in state.servers) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("protocol", s.protocol.name)
            o.put("host", s.host)
            o.put("port", s.port)
            o.put("link", s.link)
            o.put("flag", s.flag)
            o.putOpt("subscriptionId", s.subscriptionId)
            o.putOpt("unsupported", s.unsupported)
            servers.put(o)
        }
        root.put("servers", servers)

        val subs = JSONArray()
        for (s in state.subscriptions) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("url", s.url)
            o.put("updatedAt", s.updatedAt)
            o.put("upload", s.uploadBytes)
            o.put("download", s.downloadBytes)
            o.put("total", s.totalBytes)
            o.put("expireAt", s.expireAt)
            subs.put(o)
        }
        root.put("subscriptions", subs)

        val r = state.routing
        root.put("routing", JSONObject()
            .put("appMode", r.appMode.name)
            .put("apps", JSONArray(r.apps.sorted()))
            .put("directDomains", JSONArray(r.directDomains))
            .put("bypassBanks", r.bypassBanks)
            .put("directRuSites", r.directRuSites))

        val st = state.settings
        root.put("settings", JSONObject()
            .put("ipv6", st.ipv6)
            .put("dns", st.dns.name)
            .put("autoConnect", st.autoConnect))

        return root.toString()
    }

    fun fromJson(text: String): AppState {
        val root = JSONObject(text)

        val servers = ArrayList<Server>()
        val serversArr = root.optJSONArray("servers")
        if (serversArr != null) {
            for (i in 0 until serversArr.length()) {
                val o = serversArr.optJSONObject(i) ?: continue
                val protocol = Protocol.entries.find { it.name == o.optString("protocol") } ?: continue
                servers.add(Server(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    protocol = protocol,
                    host = o.optString("host"),
                    port = o.optInt("port"),
                    link = o.getString("link"),
                    flag = o.optString("flag", "🌐"),
                    subscriptionId = o.stringOrNull("subscriptionId"),
                    unsupported = o.stringOrNull("unsupported")
                ))
            }
        }

        val subs = ArrayList<Subscription>()
        val subsArr = root.optJSONArray("subscriptions")
        if (subsArr != null) {
            for (i in 0 until subsArr.length()) {
                val o = subsArr.optJSONObject(i) ?: continue
                subs.add(Subscription(
                    id = o.getString("id"),
                    name = o.optString("name"),
                    url = o.getString("url"),
                    updatedAt = o.optLong("updatedAt"),
                    uploadBytes = o.optLong("upload"),
                    downloadBytes = o.optLong("download"),
                    totalBytes = o.optLong("total"),
                    expireAt = o.optLong("expireAt")
                ))
            }
        }

        val r = root.optJSONObject("routing")
        val routing = if (r == null) Routing() else Routing(
            appMode = AppMode.entries.find { it.name == r.optString("appMode") } ?: AppMode.ALL,
            apps = r.optJSONArray("apps").toStringList().toSet(),
            directDomains = r.optJSONArray("directDomains").toStringList(),
            bypassBanks = r.optBoolean("bypassBanks", true),
            directRuSites = r.optBoolean("directRuSites", false)
        )

        val st = root.optJSONObject("settings")
        val settings = if (st == null) Settings() else Settings(
            ipv6 = st.optBoolean("ipv6", false),
            dns = RemoteDns.entries.find { it.name == st.optString("dns") } ?: RemoteDns.CLOUDFLARE,
            autoConnect = st.optBoolean("autoConnect", false)
        )

        return AppState(
            servers = servers,
            subscriptions = subs,
            selectedId = root.optString("selectedId", AUTO_SERVER_ID),
            routing = routing,
            settings = settings
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = ArrayList<String>()
        for (i in 0 until length()) {
            val s = optString(i)
            if (s.isNotEmpty()) list.add(s)
        }
        return list
    }

    private fun JSONObject.stringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotEmpty() }
    }
}
