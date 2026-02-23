package com.claustrophobDev.vinyl.ui.screens

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claustrophobDev.vinyl.R
import com.claustrophobDev.vinyl.data.AUTO_SERVER_ID
import com.claustrophobDev.vinyl.ui.MainViewModel
import com.claustrophobDev.vinyl.ui.Tab
import com.claustrophobDev.vinyl.ui.components.CircleIconButton
import com.claustrophobDev.vinyl.ui.components.DarkCard
import com.claustrophobDev.vinyl.ui.components.FlagAvatar
import com.claustrophobDev.vinyl.ui.components.PingBadge
import com.claustrophobDev.vinyl.ui.components.VinylDisc
import com.claustrophobDev.vinyl.ui.formatBytes
import com.claustrophobDev.vinyl.ui.formatDuration
import com.claustrophobDev.vinyl.ui.formatSpeed
import com.claustrophobDev.vinyl.ui.theme.VinylColors
import com.claustrophobDev.vinyl.vpn.VpnStatus
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: MainViewModel, onToggleVpn: () -> Unit) {
    val status by vm.status.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // шапка
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.vinyl_mark), contentDescription = null, modifier = Modifier.width(34.dp))
            Spacer(Modifier.width(10.dp))
            Text("VINYL", color = VinylColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp)
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Rounded.Add, "Добавить сервер", onClick = { vm.showAddSheet.value = true })
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // на маленьких экранах пластинка не должна выталкивать все остальное
            val discSize = min(maxWidth * 0.9f, maxHeight * 0.56f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StatusBlock(vm, status)
                VinylDisc(status, onToggleVpn, Modifier.size(discSize))
                TrafficRow(vm, active = status == VpnStatus.CONNECTED)
            }
        }

        ReconnectBanner(vm)
        ServerCard(vm)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun StatusBlock(vm: MainViewModel, status: VpnStatus) {
    val error by vm.error.collectAsStateWithLifecycle()

    val label = when (status) {
        VpnStatus.CONNECTED -> "Подключено"
        VpnStatus.CONNECTING -> "Подключение"
        VpnStatus.STOPPING -> "Отключение"
        VpnStatus.ERROR -> "Не удалось подключиться"
        VpnStatus.IDLE -> "Не подключено"
    }
    val color = when (status) {
        VpnStatus.CONNECTED -> VinylColors.Success
        VpnStatus.CONNECTING -> VinylColors.Warning
        VpnStatus.STOPPING -> VinylColors.TextSecondary
        VpnStatus.ERROR -> VinylColors.Danger
        VpnStatus.IDLE -> VinylColors.TextMuted
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(color.copy(alpha = 0.10f))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelLarge)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp),
            contentAlignment = Alignment.Center
        ) {
            if (status == VpnStatus.CONNECTED) {
                Uptime(vm)
            } else if (status == VpnStatus.ERROR && error != null) {
                Text(
                    text = error ?: "",
                    color = VinylColors.Danger.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else if (status == VpnStatus.IDLE) {
                Text("Коснитесь пластинки", color = VinylColors.TextMuted, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

// таймер отдельным composable, чтобы раз в секунду перерисовывался только он
@Composable
private fun Uptime(vm: MainViewModel) {
    val connectedAt by vm.connectedAt.collectAsStateWithLifecycle()
    val seconds by produceState(0L, connectedAt) {
        while (true) {
            val passed = if (connectedAt > 0) SystemClock.elapsedRealtime() - connectedAt else 0L
            value = passed / 1000
            delay(1000 - passed % 1000)
        }
    }
    Text(formatDuration(seconds), style = MaterialTheme.typography.displayMedium, color = VinylColors.Text)
}

@Composable
private fun TrafficRow(vm: MainViewModel, active: Boolean) {
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TrafficTile(
            icon = Icons.Rounded.ArrowDownward,
            label = "Загрузка",
            value = if (active) formatSpeed(traffic.downSpeed) else "-",
            total = if (active) formatBytes(traffic.downTotal) else "",
            tint = VinylColors.Success,
            modifier = Modifier.weight(1f)
        )
        TrafficTile(
            icon = Icons.Rounded.ArrowUpward,
            label = "Отдача",
            value = if (active) formatSpeed(traffic.upSpeed) else "-",
            total = if (active) formatBytes(traffic.upTotal) else "",
            tint = VinylColors.AccentBright,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TrafficTile(icon: ImageVector, label: String, value: String, total: String, tint: Color, modifier: Modifier) {
    DarkCard(modifier) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = VinylColors.TextMuted)
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = VinylColors.Text,
                    maxLines = 1
                )
                if (total.isNotEmpty()) {
                    Text(total, style = MaterialTheme.typography.bodyMedium, color = VinylColors.TextMuted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ReconnectBanner(vm: MainViewModel) {
    val needReconnect by vm.needReconnect.collectAsStateWithLifecycle()
    AnimatedVisibility(needReconnect, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val shape = RoundedCornerShape(18.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clip(shape)
                .background(VinylColors.AccentSoft)
                .border(1.dp, VinylColors.Accent.copy(alpha = 0.3f), shape)
                .clickable { vm.startVpn() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Sync, null, tint = VinylColors.AccentBright, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Изменения вступят в силу после переподключения",
                style = MaterialTheme.typography.bodyMedium,
                color = VinylColors.Text,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Text("Применить", style = MaterialTheme.typography.labelLarge, color = VinylColors.AccentBright)
        }
    }
}

@Composable
private fun ServerCard(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val pings by vm.pings.collectAsStateWithLifecycle()
    val connectedName by vm.connectedServer.collectAsStateWithLifecycle()

    val selected = state.servers.find { it.id == state.selectedId }
    val empty = state.servers.isEmpty()

    DarkCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (empty) vm.showAddSheet.value = true else vm.tab.value = Tab.SERVERS
        }
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) {
                FlagAvatar(selected.flag)
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(VinylColors.AccentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (empty) Icons.Rounded.Add else Icons.Rounded.Bolt, null, tint = VinylColors.AccentBright)
                }
            }
            Spacer(Modifier.width(14.dp))

            val title = when {
                selected != null -> selected.name
                empty -> "Добавьте сервер"
                else -> "Автовыбор"
            }
            val subtitle = when {
                selected != null -> "${selected.protocol.label} · ${selected.host}"
                empty -> "Подписка или ключ из буфера обмена"
                state.selectedId == AUTO_SERVER_ID && connectedName != null -> "Сейчас: $connectedName"
                else -> "Самый быстрый из ${state.servers.size}"
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = VinylColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = VinylColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (selected != null) {
                Spacer(Modifier.width(8.dp))
                PingBadge(pings[selected.id])
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = VinylColors.TextMuted)
        }
    }
}
