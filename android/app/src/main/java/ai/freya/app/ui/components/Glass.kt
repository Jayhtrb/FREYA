package ai.freya.app.ui.components

import ai.freya.app.ui.theme.FreyaTheme
import ai.freya.app.ui.theme.Motion
import ai.freya.app.ui.theme.Radius
import ai.freya.app.ui.theme.Space
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Full-screen backdrop: a flat, soft vertical gradient of the neumorphic surface.
 * No star field, no neon radial — depth comes from the panels themselves.
 * Drawn once per size change, so it costs nothing per frame.
 */
@Composable
fun FreyaBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = FreyaTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.backdropTop, c.backdropMid, c.backdropBottom))),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

/** Raised soft-UI panel (neumorphic). Retained name for call-site compatibility. */
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
    val style = if (pressed) NeuStyle.Sunken else NeuStyle.Raised
    Column(
        modifier = modifier
            .neumorphic(shape, style, c.surface, c.sunken, c.neuHighlight, c.neuShadow)
            .then(if (accent != null) Modifier.border(1.dp, accent.copy(alpha = 0.45f), shape) else Modifier)
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

/** Small mono status chip; flat neumorphic surface with an accent dot/label. */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = FreyaTheme.colors.cyan,
    dot: Boolean = true,
    icon: ImageVector? = null,
) {
    val c = FreyaTheme.colors
    Row(
        modifier = modifier
            .neumorphic(Radius.chip, NeuStyle.Flat, c.surface, c.sunken, c.neuHighlight, c.neuShadow)
            .padding(horizontal = 10.dp, vertical = 5.dp),
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

/** Section label, e.g. "PROVIDERS". */
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

/** Compact neumorphic action button (icon + label). */
@Composable
fun GlassAction(
    label: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    NeumorphicButton(
        label = label,
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        tint = tint,
        enabled = enabled,
    )
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
