package com.claustrophobDev.vinyl.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claustrophobDev.vinyl.data.AppInfo
import com.claustrophobDev.vinyl.data.AppMode
import com.claustrophobDev.vinyl.data.AppsRepo
import com.claustrophobDev.vinyl.ui.MainViewModel
import com.claustrophobDev.vinyl.ui.components.CircleIconButton
import com.claustrophobDev.vinyl.ui.components.DarkCard
import com.claustrophobDev.vinyl.ui.components.IconTile
import com.claustrophobDev.vinyl.ui.components.RowDivider
import com.claustrophobDev.vinyl.ui.components.ScreenHeader
import com.claustrophobDev.vinyl.ui.components.SectionLabel
import com.claustrophobDev.vinyl.ui.components.SegmentedControl
import com.claustrophobDev.vinyl.ui.components.SwitchRow
import com.claustrophobDev.vinyl.ui.theme.VinylColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutingScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val routing = state.routing
    val apps by vm.appsRepo.apps.collectAsStateWithLifecycle()

    var search by rememberSaveable { mutableStateOf("") }
    var domainText by rememberSaveable { mutableStateOf("") }

    // выбранные приложения поднимаем наверх, но порядок запоминаем при открытии экрана,
    // иначе при нажатии строка улетает вверх прямо из под пальца
    val pinned = remember { routing.apps }
    val shownApps = remember(apps, search, pinned) {
        val q = search.trim()
        apps.filter { q.isEmpty() || it.label.contains(q, true) || it.packageName.contains(q, true) }
            .sortedByDescending { it.packageName in pinned }
    }

    fun addDomain() {
        if (domainText.isNotBlank() && vm.addDomain(domainText)) domainText = ""
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            ScreenHeader("Маршруты", subtitle = "Что идет через VPN, а что напрямую")
        }

        item(key = "mode") {
            DarkCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.Apps)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Приложения", style = MaterialTheme.typography.titleMedium, color = VinylColors.Text)
                            Text(routing.appMode.hint, style = MaterialTheme.typography.bodyMedium, color = VinylColors.TextMuted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SegmentedControl(
                        options = AppMode.entries.map { it.title },
                        selected = routing.appMode.ordinal,
                        onSelect = { vm.setAppMode(AppMode.entries[it]) }
                    )
                    if (routing.appMode == AppMode.ONLY && routing.apps.isEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Отметьте приложения ниже. Пока ничего не выбрано, VPN работает для всех",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VinylColors.Warning
                        )
                    }
                }
            }
        }

        item(key = "direct-label") { SectionLabel("Напрямую, без VPN") }
        item(key = "presets") {
            DarkCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                SwitchRow(
                    Icons.Rounded.AccountBalance,
                    "Банки и Госуслуги",
                    "Сбер, Т-Банк, Альфа, ВТБ, Mir Pay и другие",
                    routing.bypassBanks
                ) { vm.setBypassBanks(it) }
                RowDivider()
                SwitchRow(
                    Icons.Rounded.Language,
                    "Российские сайты",
                    ".ru, .рф, Яндекс, VK, Mail.ru",
                    routing.directRuSites
                ) { vm.setDirectRuSites(it) }
            }
        }

        item(key = "domains-label") { SectionLabel("Свои сайты напрямую") }
        item(key = "domains") {
            DarkCard(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shape = RoundedCornerShape(14.dp)
                        Box(
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(shape)
                                .background(VinylColors.Background)
                                .border(1.dp, VinylColors.Stroke, shape)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (domainText.isEmpty()) {
                                Text("kinopoisk.ru", style = MaterialTheme.typography.bodyLarge, color = VinylColors.TextMuted)
                            }
                            BasicTextField(
                                value = domainText,
                                onValueChange = { domainText = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = VinylColors.Text),
                                cursorBrush = SolidColor(VinylColors.Accent),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { addDomain() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        CircleIconButton(
                            Icons.Rounded.Add,
                            "Добавить сайт",
                            onClick = { addDomain() },
                            tint = Color.White,
                            background = VinylColors.Accent,
                            size = 48.dp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (routing.directDomains.isEmpty()) {
                        Text(
                            "Сайт и все его поддомены будут открываться без VPN",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VinylColors.TextMuted
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (domain in routing.directDomains) {
                                DomainChip(domain, onRemove = { vm.removeDomain(domain) })
                            }
                        }
                    }
                }
            }
        }

        if (routing.appMode != AppMode.ALL) {
            item(key = "apps-label") {
                val title = if (routing.appMode == AppMode.ONLY) "Через VPN" else "Без VPN"
                SectionLabel("$title · ${routing.apps.size}")
            }
            item(key = "apps-search") {
                SearchField(search, { search = it }, "Поиск приложения", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            items(shownApps, key = { it.packageName }, contentType = { "app" }) { app ->
                AppItem(
                    app = app,
                    checked = app.packageName in routing.apps,
                    repo = vm.appsRepo,
                    onToggle = { vm.toggleApp(app.packageName) }
                )
            }
        }
    }
}

@Composable
private fun DomainChip(domain: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(VinylColors.SurfaceHigh)
            .border(1.dp, VinylColors.Stroke, CircleShape)
            .clickable(onClick = onRemove)
            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(domain, style = MaterialTheme.typography.bodyMedium, color = VinylColors.Text)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.Close, "Убрать", tint = VinylColors.TextMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun AppItem(app: AppInfo, checked: Boolean, repo: AppsRepo, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.packageName, repo)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, color = VinylColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.bodyMedium, color = VinylColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        CheckCircle(checked)
    }
}

@Composable
private fun AppIcon(pkg: String, repo: AppsRepo) {
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    // иконка грузится в фоне, пока нет рисуем пустой квадрат
    val icon by produceState(repo.getCachedIcon(pkg), pkg) {
        if (value == null) value = repo.loadIcon(pkg, sizePx)
    }
    val shape = RoundedCornerShape(12.dp)
    val bmp = icon
    if (bmp != null) {
        Image(bmp, contentDescription = null, modifier = Modifier.size(40.dp).clip(shape))
    } else {
        Box(
            Modifier
                .size(40.dp)
                .clip(shape)
                .background(VinylColors.SurfaceHigh)
        )
    }
}

@Composable
private fun CheckCircle(checked: Boolean) {
    val bg by animateColorAsState(if (checked) VinylColors.Accent else Color.Transparent, tween(160), label = "checkBg")
    val border by animateColorAsState(if (checked) VinylColors.Accent else VinylColors.StrokeStrong, tween(160), label = "checkBorder")
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
