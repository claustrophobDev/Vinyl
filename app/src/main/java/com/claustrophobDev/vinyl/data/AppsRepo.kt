package com.claustrophobDev.vinyl.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

// список приложений для раздельного туннелирования
// грузим один раз при старте, иконки отдельно и только для видимых строк
class AppsRepo(private val context: Context) {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val iconCache = LruCache<String, ImageBitmap>(200)

    @Volatile
    private var loading = false

    fun load(scope: CoroutineScope) {
        if (loading) return
        loading = true
        scope.launch(Dispatchers.IO) {
            _apps.value = try {
                queryApps()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun queryApps(): List<AppInfo> {
        val pm = context.packageManager
        // берем только то что есть в лаунчере, системные сервисы в списке не нужны
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        val collator = Collator.getInstance()
        return list.map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { AppInfo(it.packageName, it.loadLabel(pm).toString().ifBlank { it.packageName }) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun getCachedIcon(pkg: String): ImageBitmap? = iconCache.get(pkg)

    suspend fun loadIcon(pkg: String, sizePx: Int): ImageBitmap? {
        iconCache.get(pkg)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val bmp = context.packageManager.getApplicationIcon(pkg).toBitmap(sizePx, sizePx).asImageBitmap()
                iconCache.put(pkg, bmp)
                bmp
            } catch (e: Exception) {
                null
            }
        }
    }
}
