package com.claustrophobDev.vinyl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claustrophobDev.vinyl.core.Ping
import com.claustrophobDev.vinyl.ui.theme.VinylColors

private val cardShape = RoundedCornerShape(24.dp)

@Composable
fun DarkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var m = modifier
        .clip(cardShape)
        .background(VinylColors.Surface)
        .border(1.dp, VinylColors.Stroke, cardShape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m, content = content)
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = VinylColors.Text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = VinylColors.TextMuted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = VinylColors.TextMuted,
        modifier = modifier.padding(start = 28.dp, end = 24.dp, top = 20.dp, bottom = 10.dp)
    )
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = VinylColors.Text,
    background: Color = VinylColors.Surface,
    size: Dp = 44.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, VinylColors.Stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(18.dp)
    val bg = if (enabled) {
        Brush.horizontalGradient(listOf(VinylColors.AccentDeep, VinylColors.Accent))
    } else {
        Brush.horizontalGradient(listOf(VinylColors.SurfaceHigh, VinylColors.SurfaceHigh))
    }
    val color = if (enabled) Color.White else VinylColors.TextMuted
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(VinylColors.SurfaceHigh)
            .border(1.dp, VinylColors.Stroke, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = VinylColors.AccentBright, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = VinylColors.Text)
    }
}

@Composable
fun IconTile(icon: ImageVector, tint: Color = VinylColors.AccentBright, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun FlagAvatar(flag: String, size: Dp = 44.dp, dimmed: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(VinylColors.SurfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        Text(flag, fontSize = (size.value * 0.5f).sp, color = if (dimmed) VinylColors.TextMuted else Color.Unspecified)
    }
}

@Composable
fun PingBadge(ms: Int?, modifier: Modifier = Modifier) {
    val text: String
    val color: Color
    when {
        ms == null -> { text = "-"; color = VinylColors.TextMuted }
        ms == Ping.UDP -> { text = "UDP"; color = VinylColors.TextMuted }
        ms < 0 -> { text = "нет ответа"; color = VinylColors.Danger }
        ms < 150 -> { text = "$ms мс"; color = VinylColors.Success }
        ms < 400 -> { text = "$ms мс"; color = VinylColors.Warning }
        else -> { text = "$ms мс"; color = VinylColors.Danger }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, color: Color = VinylColors.AccentBright) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun VinylSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = VinylColors.Accent,
            checkedBorderColor = VinylColors.Accent,
            uncheckedThumbColor = VinylColors.TextSecondary,
            uncheckedTrackColor = VinylColors.SurfaceHigh,
            uncheckedBorderColor = VinylColors.StrokeStrong
        )
    )
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    var m = modifier.fillMaxWidth()
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(
        modifier = m.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = VinylColors.Text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = VinylColors.TextMuted)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingRow(icon, title, subtitle, onClick = { onCheckedChange(!checked) }) {
        VinylSwitch(checked, onCheckedChange)
    }
}

@Composable
fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 70.dp)
            .height(1.dp)
            .background(VinylColors.Stroke)
    )
}

// переключатель с ползунком, offset в лямбде чтобы анимация не дергала рекомпозицию
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(VinylColors.Background)
            .border(1.dp, VinylColors.Stroke, shape)
            .padding(4.dp)
    ) {
        val itemWidth = maxWidth / options.size
        val sliderX by animateDpAsState(itemWidth * selected, tween(260), label = "segment")

        Box(
            Modifier
                .offset { IntOffset(sliderX.roundToPx(), 0) }
                .width(itemWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(VinylColors.AccentSoft)
                .border(BorderStroke(1.dp, VinylColors.Accent.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
        )

        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val textColor by animateColorAsState(
                    if (index == selected) VinylColors.Text else VinylColors.TextMuted,
                    tween(200),
                    label = "segmentText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        option,
                        color = textColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
