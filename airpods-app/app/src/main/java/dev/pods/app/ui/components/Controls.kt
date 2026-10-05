package dev.pods.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pods.app.ui.theme.Pods
import dev.pods.app.ui.theme.PodsType
import dev.pods.app.ui.theme.SquircleShape

/** An inset grouped section, like the ones in iOS Settings. */
@Composable
fun Section(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Pods.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (header != null) {
            Text(
                text = header.uppercase(),
                style = PodsType.sectionHeader,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 7.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(SquircleShape(12.dp))
                .background(colors.card),
            content = content,
        )
        if (footer != null) {
            Text(
                text = footer,
                style = PodsType.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
    }
}

/** Colored rounded square holding a white glyph, as used for iOS settings rows. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 29.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(SquircleShape(size * 0.24f))
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.64f))
    }
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Pods.colors.blue,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = Pods.colors.label,
    chevron: Boolean = false,
    divider: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Pods.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val highlight by animateColorAsState(
        targetValue = if (pressed && onClick != null) colors.cardPressed else colors.card,
        animationSpec = tween(if (pressed) 0 else 280),
        label = "rowHighlight",
    )
    Box(
        modifier
            .fillMaxWidth()
            .background(highlight)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .padding(start = 16.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                IconTile(icon, iconTint)
                Spacer(Modifier.width(13.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 11.dp)
            ) {
                Text(title, style = PodsType.body, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = PodsType.footnote, color = colors.secondaryLabel)
                }
            }
            if (value != null) {
                Spacer(Modifier.width(8.dp))
                Text(value, style = PodsType.body, color = colors.secondaryLabel, maxLines = 1)
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (chevron) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.tertiaryLabel,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (divider) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = if (icon != null) 58.dp else 16.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.separator)
            )
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconTint: Color = Pods.colors.blue,
    subtitle: String? = null,
    divider: Boolean = true,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        icon = icon,
        iconTint = iconTint,
        subtitle = subtitle,
        divider = divider,
        enabled = enabled,
        trailing = { IosSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
    )
}

/** The familiar green pill switch. */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val colors = Pods.colors
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "switch",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val knobStretch by animateFloatAsState(if (pressed) 1f else 0f, label = "knob")
    Box(
        modifier = Modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(lerp(colors.switchOff, colors.green, progress))
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val knobWidth = 27.dp + 6.dp * knobStretch
        Box(
            Modifier
                .offset(x = (47.dp - knobWidth) * progress)
                .size(width = knobWidth, height = 27.dp)
                .shadow(elevation = 3.dp, shape = CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(Color.White, CircleShape)
        )
    }
}

/** Big rounded call-to-action button. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Pods.colors.blue) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.75f else 1f, tween(if (pressed) 0 else 200), label = "btn")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(SquircleShape(14.dp))
            .background(color)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PodsType.headline, color = Color.White)
    }
}

/** Plain text button in the accent color. */
@Composable
fun TextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Pods.colors.blue) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Text(
        text = text,
        style = PodsType.headline,
        color = color.copy(alpha = if (pressed) 0.5f else 1f),
        modifier = modifier
            .clip(SquircleShape(8.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** Horizontal arrangement helper used by a few cards. */
val SpacedEvenly = Arrangement.SpaceEvenly

data class Segment(val label: String, val icon: ImageVector)

/**
 * iOS-style segmented control with a sliding selection pill. Used for noise
 * control, where each segment shows an icon above a short label.
 */
@Composable
fun SegmentedControl(
    segments: List<Segment>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Pods.colors
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(SquircleShape(14.dp))
            .background(colors.fill)
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / segments.size
        val target = (selectedIndex ?: 0).coerceIn(0, segments.lastIndex)
        val offset by androidx.compose.animation.core.animateDpAsState(
            targetValue = segmentWidth * target,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            label = "segment",
        )
        if (selectedIndex != null) {
            Box(
                Modifier
                    .offset(x = offset)
                    .size(width = segmentWidth, height = 64.dp)
                    .shadow(2.dp, SquircleShape(11.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .background(if (colors.isDark) colors.cardPressed else Color.White, SquircleShape(11.dp))
            )
        }
        Row(Modifier.fillMaxWidth()) {
            segments.forEachIndexed { index, segment ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .width(segmentWidth)
                        .height(64.dp)
                        .clip(SquircleShape(11.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        segment.icon,
                        contentDescription = null,
                        tint = if (selected) colors.blue else colors.secondaryLabel,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        segment.label,
                        style = PodsType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        color = if (selected) colors.label else colors.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
