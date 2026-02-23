package com.claustrophobDev.vinyl.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claustrophobDev.vinyl.R
import com.claustrophobDev.vinyl.data.RemoteDns
import com.claustrophobDev.vinyl.ui.MainViewModel
import com.claustrophobDev.vinyl.ui.components.DarkCard
import com.claustrophobDev.vinyl.ui.components.RowDivider
import com.claustrophobDev.vinyl.ui.components.ScreenHeader
import com.claustrophobDev.vinyl.ui.components.SecondaryButton
import com.claustrophobDev.vinyl.ui.components.SectionLabel
import com.claustrophobDev.vinyl.ui.components.SegmentedControl
import com.claustrophobDev.vinyl.ui.components.SettingRow
import com.claustrophobDev.vinyl.ui.components.SwitchRow
import com.claustrophobDev.vinyl.ui.theme.VinylColors
import com.claustrophobDev.vinyl.vpn.LogLevel
import com.claustrophobDev.vinyl.vpn.VpnLog
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val context = LocalContext.current
    var showLogs by rememberSaveable { mutableStateOf(false) }

    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") { ScreenHeader("Настройки") }

        item(key = "connection-label") { SectionLabel("Подключение") }
        item(key = "connection") {
            DarkCard(cardModifier) {
                SwitchRow(
                    Icons.Rounded.PowerSettingsNew,
                    "Подключать при запуске",
                    "Включать VPN, когда открываете Vinyl",
                    settings.autoConnect
                ) { on -> vm.updateSettings { it.copy(autoConnect = on) } }

                RowDivider()

                // своего kill switch нет, андроид умеет это сам через "постоянный vpn"
                SettingRow(
                    Icons.Rounded.Shield,
                    "Kill switch",
                    "Постоянный VPN и блокировка без VPN (в настройках Android)",
                    onClick = {
                        try {
                            context.startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
                        } catch (e: Exception) {
                            vm.message("Не удалось открыть настройки VPN")
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = VinylColors.TextMuted)
                }

                RowDivider()

                SwitchRow(
                    Icons.Rounded.Public,
                    "IPv6",
                    "Отдавать приложениям IPv6-адреса",
                    settings.ipv6
                ) { on -> vm.updateSettings { it.copy(ipv6 = on) } }
            }
        }

        item(key = "dns-label") { SectionLabel("DNS") }
        item(key = "dns") {
            DarkCard(cardModifier) {
                Column(Modifier.padding(16.dp)) {
                    SegmentedControl(
                        options = RemoteDns.entries.map { it.title },
                        selected = settings.dns.ordinal,
                        onSelect = { i -> vm.updateSettings { it.copy(dns = RemoteDns.entries[i]) } }
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Запросы шифруются (DNS-over-HTTPS) и уходят через сервер VPN",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VinylColors.TextMuted
                    )
                }
            }
        }

        item(key = "diag-label") { SectionLabel("Диагностика") }
        item(key = "logs") {
            DarkCard(cardModifier) {
                SettingRow(
                    Icons.Rounded.Terminal,
                    "Журнал",
                    "Ошибки подключения и сообщения ядра",
                    onClick = { showLogs = !showLogs }
                ) {
                    Icon(
                        if (showLogs) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        null,
                        tint = VinylColors.TextMuted
                    )
                }
                if (showLogs) LogsBlock(vm)
            }
        }

        item(key = "footer") { AboutFooter() }
    }
}

@Composable
private fun LogsBlock(vm: MainViewModel) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    val shape = RoundedCornerShape(14.dp)

    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .clip(shape)
                .background(VinylColors.Background)
                .border(1.dp, VinylColors.Stroke, shape)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (logs.isEmpty()) {
                Text("Пока пусто", color = VinylColors.TextMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            // новые сверху, больше 150 строк не рисуем
            for (line in logs.asReversed().take(150)) {
                val color = when (line.level) {
                    LogLevel.ERROR -> VinylColors.Danger
                    LogLevel.WARN -> VinylColors.Warning
                    LogLevel.INFO -> VinylColors.TextSecondary
                }
                Text(
                    text = timeFormat.format(line.time) + "  " + line.text,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                "Копировать",
                onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("Vinyl log", VpnLog.asText()))
                    vm.message("Журнал скопирован")
                },
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ContentCopy
            )
            SecondaryButton("Очистить", { vm.clearLogs() }, Modifier.weight(1f), icon = Icons.Rounded.DeleteOutline)
        }
    }
}

@Composable
private fun AboutFooter() {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painterResource(R.drawable.vinyl_mark),
            null,
            Modifier
                .width(56.dp)
                .graphicsLayer { alpha = 0.7f }
        )
        Spacer(Modifier.height(10.dp))
        Text("VINYL", color = VinylColors.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp)
        if (version.isNotEmpty()) {
            Text("версия $version", style = MaterialTheme.typography.bodyMedium, color = VinylColors.TextMuted)
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .clip(CircleShape)
                .background(VinylColors.Surface)
                .border(1.dp, VinylColors.Stroke, CircleShape)
                .clickable {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/claustrophobDev")))
                    } catch (e: Exception) {
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Developer", style = MaterialTheme.typography.labelMedium, color = VinylColors.TextMuted)
            Spacer(Modifier.width(6.dp))
            Text(
                "claustrophobDev",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = VinylColors.AccentBright
            )
        }
    }
}
