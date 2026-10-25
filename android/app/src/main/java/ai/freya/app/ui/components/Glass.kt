package ai.freya.app.ui.components

import ai.freya.app.ui.theme.FreyaTheme
import ai.freya.app.ui.theme.Motion
import ai.freya.app.ui.theme.Radius
import ai.freya.app.ui.theme.Space
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Full-screen cinematic backdrop: a vertical void→midnight gradient, a soft
 * radial "stage light" behind the core, and a fixed sparse star field.
 * Everything is drawn once per size change (no animation) so it costs nothing per frame.
 */
@Composable
fun FreyaBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = FreyaTheme.colors
    val stars = remember {
        val r = Random(7)
        List(70) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(c.backdropTop, c.backdropMid, c.backdropBottom)))
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(c.violet.copy(alpha = if (c.isDark) 0.10f else 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.30f),
                        radius = size.minDimension * 0.85f,
                    )
                )
                if (c.isDark) {
                    stars.forEach { (x, y, s) ->
                        drawCircle(
                            color = c.ice.copy(alpha = 0.10f + s * 0.25f),
                            radius = 0.6f + s * 1.1f,
                            center = Offset(x * size.width, y * size.height),
                        )
                    }
                }
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

/** Translucent panel with a hairline border and top-edge sheen. No real blur (cheap on every device). */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.panel,
    accent: Color? = null,
    contentPadding: Dp = Space.lg,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = FreyaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(
        if (pressed) c.glassFillStrong else c.glassFill,
        tween(Motion.micro),
        label = "glassFill",
    )
    val border = accent?.copy(alpha = 0.45f) ?: c.glassBorder
    Column(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .background(Brush.verticalGradient(listOf(c.glassHighlight, Color.Transparent), endY = 120f))
            .border(BorderStroke(1.dp, border), shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ) else Modifier
            )
            .padding(contentPadding),
        content = content,
    )
}

/** Small mono status chip used for telemetry (model, latency, state). */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = FreyaTheme.colors.cyan,
    dot: Boolean = true,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(Radius.chip)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.28f), Radius.chip)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
        } else if (dot) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, softWrap = false)
    }
}

/** Section label in the telemetry face, e.g. "PROVIDERS". */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = FreyaTheme.colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke(this)
    }
}

/** Compact glass action button (icon + label). */
@Composable
fun GlassAction(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    val c = FreyaTheme.colors
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(Radius.pill)
            .background(c.glassFill)
            .border(1.dp, c.glassBorder, Radius.pill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val t = if (enabled) tint else tint.copy(alpha = 0.38f)
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = t, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = t)
    }
}

/** Key/value row used across the Control Center. */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Space.md))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}
