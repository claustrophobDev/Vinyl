package com.claustrophobDev.vinyl.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.claustrophobDev.vinyl.ui.screens.AddSheet
import com.claustrophobDev.vinyl.ui.screens.HomeScreen
import com.claustrophobDev.vinyl.ui.screens.RoutingScreen
import com.claustrophobDev.vinyl.ui.screens.ServersScreen
import com.claustrophobDev.vinyl.ui.screens.SettingsScreen
import com.claustrophobDev.vinyl.ui.theme.VinylColors
import kotlinx.coroutines.launch

@Composable
fun AppRoot(vm: MainViewModel, onToggleVpn: () -> Unit) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val showAddSheet by vm.showAddSheet.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val stateHolder = rememberSaveableStateHolder()

    LaunchedEffect(vm) {
        vm.messages.collect { text ->
            launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(text)
            }
        }
    }

    BackHandler(enabled = tab != Tab.HOME) {
        vm.tab.value = Tab.HOME
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(VinylColors.Background)
    ) {
        BackgroundGlow()

        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                    label = "tab"
                ) { current ->
                    // чтобы скролл и поиск на вкладках не сбрасывались при переключении
                    stateHolder.SaveableStateProvider(current.name) {
                        when (current) {
                            Tab.HOME -> HomeScreen(vm, onToggleVpn)
                            Tab.SERVERS -> ServersScreen(vm)
                            Tab.ROUTING -> RoutingScreen(vm)
                            Tab.SETTINGS -> SettingsScreen(vm)
                        }
                    }
                }
            }
            BottomBar(tab, onSelect = { vm.tab.value = it })
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 92.dp)
        ) { data ->
            val shape = RoundedCornerShape(18.dp)
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyLarge,
                color = VinylColors.Text,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(shape)
                    .background(VinylColors.SurfaceHigh)
                    .border(1.dp, VinylColors.StrokeStrong, shape)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            )
        }

        if (showAddSheet) {
            AddSheet(vm, onDismiss = { vm.showAddSheet.value = false })
        }
    }
}

// фиолетовое свечение по углам как на логотипе
@Composable
private fun BackgroundGlow() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                listOf(Color(0x2E5B3DF5), Color.Transparent),
                center = Offset(0f, 0f),
                radius = size.width * 0.95f
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(Color(0x245B3DF5), Color.Transparent),
                center = Offset(size.width, size.height),
                radius = size.width * 1.05f
            )
        )
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(shape)
            .background(VinylColors.Surface)
            .border(1.dp, VinylColors.Stroke, shape)
            .padding(6.dp)
    ) {
        for (tab in Tab.entries) {
            val isSelected = tab == selected
            val bg by animateColorAsState(if (isSelected) VinylColors.AccentSoft else Color.Transparent, tween(200), label = "navBg")
            val tint by animateColorAsState(if (isSelected) VinylColors.AccentBright else VinylColors.TextMuted, tween(200), label = "navTint")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(tabIcon(tab), contentDescription = tabTitle(tab), tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(3.dp))
                Text(
                    text = tabTitle(tab),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) VinylColors.Text else VinylColors.TextMuted
                )
            }
        }
    }
}

private fun tabTitle(tab: Tab) = when (tab) {
    Tab.HOME -> "Главная"
    Tab.SERVERS -> "Серверы"
    Tab.ROUTING -> "Маршруты"
    Tab.SETTINGS -> "Настройки"
}

private fun tabIcon(tab: Tab): ImageVector = when (tab) {
    Tab.HOME -> Icons.Rounded.Album
    Tab.SERVERS -> Icons.Rounded.Dns
    Tab.ROUTING -> Icons.AutoMirrored.Rounded.AltRoute
    Tab.SETTINGS -> Icons.Rounded.Tune
}
