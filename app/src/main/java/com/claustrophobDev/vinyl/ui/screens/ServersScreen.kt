package com.claustrophobDev.vinyl.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claustrophobDev.vinyl.R
import com.claustrophobDev.vinyl.data.AUTO_SERVER_ID
import com.claustrophobDev.vinyl.data.AppState
import com.claustrophobDev.vinyl.data.Server
import com.claustrophobDev.vinyl.data.Subscription
import com.claustrophobDev.vinyl.ui.MainViewModel
import com.claustrophobDev.vinyl.ui.components.Chip
import com.claustrophobDev.vinyl.ui.components.CircleIconButton
import com.claustrophobDev.vinyl.ui.components.FlagAvatar
import com.claustrophobDev.vinyl.ui.components.PingBadge
import com.claustrophobDev.vinyl.ui.components.PrimaryButton
import com.claustrophobDev.vinyl.ui.components.ScreenHeader
import com.claustrophobDev.vinyl.ui.components.SecondaryButton
import com.claustrophobDev.vinyl.ui.formatAgo
import com.claustrophobDev.vinyl.ui.formatBytes
import com.claustrophobDev.vinyl.ui.formatDate
import com.claustrophobDev.vinyl.ui.serversWord
import com.claustrophobDev.vinyl.ui.theme.VinylColors
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

private class Group(val key: String, val sub: Subscription?, val servers: List<Server>)

@Composable
fun ServersScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val pings by vm.pings.collectAsStateWithLifecycle()
    val pinging by vm.pinging.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var search by rememberSaveable { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Server?>(null) }
    val groups = remember(state.servers, state.subscriptions, search) { makeGroups(state, search) }

    Column(Modifier.fillMaxSize()) {
        val count = state.servers.size
        ScreenHeader(title = "Серверы", subtitle = if (count > 0) "$count ${serversWord(count)}" else null) {
            if (count > 0) {
                CircleIconButton(
                    Icons.Rounded.Speed, "Проверить пинг",
                    onClick = { vm.pingAll() },
                    tint = if (pinging) VinylColors.AccentBright else VinylColors.Text
                )
            }
            if (state.subscriptions.isNotEmpty()) {
                CircleIconButton(Icons.Rounded.Refresh, "Обновить подписки", onClick = { vm.refreshAll() })
            }
            CircleIconButton(
                Icons.Rounded.Add, "Добавить",
                onClick = { vm.showAddSheet.value = true },
                tint = Color.White,
                background = VinylColors.Accent
            )
        }

        if (!loaded) return@Column

        if (state.servers.isEmpty() && state.subscriptions.isEmpty()) {
            EmptyState(
                onPaste = { pasteFromClipboard(context, vm) },
                onAdd = { vm.showAddSheet.value = true }
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            // поиск показываем только когда серверов много
            if (state.servers.size > 8) {
                item(key = "search", contentType = "search") {
                    SearchField(search, { search = it }, "Страна, название или протокол", Modifier.padding(vertical = 6.dp))
                }
            }
            if (search.isBlank()) {
                item(key = "auto", contentType = "auto") {
                    AutoItem(selected = state.selectedId == AUTO_SERVER_ID, onClick = { vm.select(AUTO_SERVER_ID) })
                }
            }
            for (group in groups) {
                item(key = "group-${group.key}", contentType = "header") {
                    GroupHeader(
                        group = group,
                        refreshing = group.sub?.id in refreshing,
                        onRefresh = { group.sub?.let { vm.refresh(it) } },
                        onDelete = { group.sub?.let { vm.deleteSubscription(it.id) } }
                    )
                }
                items(group.servers, key = { it.id }, contentType = { "server" }) { server ->
                    ServerItem(
                        server = server,
                        selected = server.id == state.selectedId,
                        ping = pings[server.id],
                        onClick = {
                            if (server.unsupported != null) vm.message(server.unsupported) else vm.select(server.id)
                        },
                        onLongClick = { toDelete = server }
                    )
                }
            }
        }
    }

    val server = toDelete
    if (server != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = VinylColors.SurfaceHigh,
            title = { Text("Удалить сервер?", color = VinylColors.Text) },
            text = {
                val text = if (server.subscriptionId != null) {
                    "${server.name} вернется после обновления подписки"
                } else {
                    server.name
                }
                Text(text, color = VinylColors.TextSecondary)
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteServer(server.id)
                    toDelete = null
                }) {
                    Text("Удалить", color = VinylColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text("Отмена", color = VinylColors.TextSecondary)
                }
            }
        )
    }
}

// группируем серверы по подпискам, добавленные руками в конце
private fun makeGroups(state: AppState, search: String): List<Group> {
    val q = search.trim()
    fun matches(s: Server): Boolean {
        if (q.isEmpty()) return true
        return s.name.contains(q, true) || s.host.contains(q, true) || s.protocol.label.contains(q, true)
    }

    val bySub = state.servers.groupBy { it.subscriptionId }
    val subIds = state.subscriptions.map { it.id }.toHashSet()

    val result = ArrayList<Group>()
    for (sub in state.subscriptions) {
        val list = bySub[sub.id].orEmpty().filter { matches(it) }
        if (list.isEmpty() && q.isNotEmpty()) continue
        result.add(Group(sub.id, sub, list))
    }
    val manual = state.servers.filter { (it.subscriptionId == null || it.subscriptionId !in subIds) && matches(it) }
    if (manual.isNotEmpty()) result.add(Group("manual", null, manual))
    return result
}

private fun pasteFromClipboard(context: Context, vm: MainViewModel): Boolean {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
    val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString()?.trim() else null
    if (text.isNullOrEmpty()) {
        vm.message("Буфер обмена пуст")
        return false
    }
    vm.addFromText(text)
    return true
}

@Composable
private fun AutoItem(selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(shape)
            .background(if (selected) VinylColors.AccentSoft else VinylColors.Surface)
            .border(1.dp, if (selected) VinylColors.Accent.copy(alpha = 0.45f) else VinylColors.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(VinylColors.Accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Bolt, null, tint = VinylColors.AccentBright)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Автовыбор", style = MaterialTheme.typography.titleMedium, color = VinylColors.Text)
            Text("Подключаться к серверу с наименьшим пингом", style = MaterialTheme.typography.bodyMedium, color = VinylColors.TextMuted)
        }
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, null, tint = VinylColors.AccentBright, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun GroupHeader(group: Group, refreshing: Boolean, onRefresh: () -> Unit, onDelete: () -> Unit) {
    val sub = group.sub
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 18.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    sub?.name ?: "Добавлены вручную",
                    style = MaterialTheme.typography.titleLarge,
                    color = VinylColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val count = group.servers.size
                var info = "$count ${serversWord(count)}"
                if (sub != null) {
                    info += " · " + if (refreshing) "обновляется" else formatAgo(sub.updatedAt)
                }
                Text(info, style = MaterialTheme.typography.bodyMedium, color = VinylColors.TextMuted)
            }

            if (sub != null) {
                Box {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MoreVert, "Действия", tint = VinylColors.TextSecondary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = VinylColors.SurfaceHigh) {
                        DropdownMenuItem(
                            text = { Text("Обновить", color = VinylColors.Text) },
                            onClick = {
                                menuOpen = false
                                onRefresh()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить подписку", color = VinylColors.Danger) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }

        // сколько трафика осталось и до какого числа, если панель это отдает
        if (sub != null && (sub.totalBytes > 0 || sub.expireAt > 0)) {
            Spacer(Modifier.height(10.dp))
            if (sub.totalBytes > 0) {
                val used = sub.uploadBytes + sub.downloadBytes
                val part = (used.toFloat() / sub.totalBytes).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(VinylColors.SurfaceHigh)
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(part)
                            .background(if (part > 0.9f) VinylColors.Danger else VinylColors.Accent)
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            val parts = ArrayList<String>()
            if (sub.totalBytes > 0) parts.add("${formatBytes(sub.uploadBytes + sub.downloadBytes)} из ${formatBytes(sub.totalBytes)}")
            if (sub.expireAt > 0) parts.add("до ${formatDate(sub.expireAt)}")
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = VinylColors.TextSecondary)
        }
    }
}

@Composable
private fun ServerItem(server: Server, selected: Boolean, ping: Int?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val notSupported = server.unsupported != null

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(if (selected) VinylColors.AccentSoft else VinylColors.Surface)
            .border(1.dp, if (selected) VinylColors.Accent.copy(alpha = 0.45f) else VinylColors.Stroke, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlagAvatar(server.flag, size = 40.dp, dimmed = notSupported)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                server.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (notSupported) VinylColors.TextMuted else VinylColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(server.protocol.label, color = if (notSupported) VinylColors.TextMuted else VinylColors.AccentBright)
                Spacer(Modifier.width(6.dp))
                Text(
                    server.unsupported ?: server.host,
                    style = MaterialTheme.typography.bodyMedium,
                    color = VinylColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!notSupported) {
            Spacer(Modifier.width(8.dp))
            PingBadge(ping)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.CheckCircle, null, tint = VinylColors.AccentBright, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun EmptyState(onPaste: () -> Unit, onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painterResource(R.drawable.vinyl_mark),
            null,
            Modifier
                .width(120.dp)
                .graphicsLayer { alpha = 0.4f }
        )
        Spacer(Modifier.height(24.dp))
        Text("Пока пусто", style = MaterialTheme.typography.titleLarge, color = VinylColors.Text)
        Spacer(Modifier.height(8.dp))
        Text(
            "Скопируйте ссылку на подписку или ключ vless://, trojan://, ss://... и вставьте сюда",
            style = MaterialTheme.typography.bodyLarge,
            color = VinylColors.TextMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Вставить из буфера", onPaste, Modifier.fillMaxWidth(), icon = Icons.Rounded.ContentPaste)
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Ввести вручную", onAdd, Modifier.fillMaxWidth())
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(VinylColors.Surface)
            .border(1.dp, VinylColors.Stroke, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = VinylColors.TextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = VinylColors.TextMuted, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = VinylColors.Text),
                cursorBrush = SolidColor(VinylColors.Accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close,
                "Очистить",
                tint = VinylColors.TextMuted,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onValueChange("") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }

    // сначала анимация закрытия, потом убираем шторку
    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    // сканер открывается своей активити и возвращает результат сюда
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val value = result.contents
        if (value.isNullOrBlank()) {
            // null значит юзер нажал назад, ничего не показываем
            if (result.contents == "") vm.message("В QR-коде ничего нет")
        } else {
            vm.addFromText(value)
            close()
        }
    }

    fun scanQr() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .setPrompt("Наведите камеру на QR-код")
        scanLauncher.launch(options)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VinylColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = VinylColors.StrokeStrong) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
        ) {
            Text("Добавить сервер", style = MaterialTheme.typography.titleLarge, color = VinylColors.Text)
            Spacer(Modifier.height(6.dp))
            Text(
                "Ссылка на подписку или ключи vless, vmess, trojan, ss, hy2, tuic. Можно сразу несколько, каждый с новой строки",
                style = MaterialTheme.typography.bodyMedium,
                color = VinylColors.TextMuted
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                "Вставить из буфера",
                onClick = { if (pasteFromClipboard(context, vm)) close() },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.ContentPaste
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                "Сканировать QR",
                onClick = { scanQr() },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.QrCodeScanner
            )
            Spacer(Modifier.height(14.dp))

            val shape = RoundedCornerShape(18.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp)
                    .clip(shape)
                    .background(VinylColors.Background)
                    .border(1.dp, VinylColors.Stroke, shape)
                    .padding(14.dp)
            ) {
                if (text.isEmpty()) {
                    Text("vless://... или https://.../sub", style = MaterialTheme.typography.bodyMedium, color = VinylColors.TextMuted)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = VinylColors.Text),
                    cursorBrush = SolidColor(VinylColors.Accent),
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(14.dp))
            SecondaryButton(
                "Добавить",
                onClick = {
                    if (text.isBlank()) {
                        vm.message("Вставьте ссылку в поле")
                    } else {
                        vm.addFromText(text)
                        close()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Add
            )
        }
    }
}
