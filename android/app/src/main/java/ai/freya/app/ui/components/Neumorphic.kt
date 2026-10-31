package ai.freya.app.ui.components

import ai.freya.app.ui.theme.FreyaTheme
import ai.freya.app.ui.theme.Neumorphic
import ai.freya.app.ui.theme.Radius
import ai.freya.app.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Soft-UI surface style. Depth is drawn with dual shadows, never blur or neon. */
enum class NeuStyle { Raised, Flat, Sunken }

/**
 * Core neumorphic surface modifier. Surfaces are the SAME colour as the
 * background; depth is a soft light highlight (top-left) + soft dark shadow
 * (bottom-right) for raised elements, and the inverse (inset) for sunken ones.
 * Cheap: a single `shadow` (GPU-composited) plus two gradient fills, no `blur`.
 */
fun Modifier.neumorphic(
    shape: Shape,
    style: NeuStyle,
    surface: Color,
    sunken: Color,
    highlight: Color,
    shadow: Color,
): Modifier = this.then(
    when (style) {
        NeuStyle.Raised -> Modifier
            .shadow(Neumorphic.raisedElevation, shape, spotColor = shadow, ambientColor = shadow)
            .background(surface, shape)
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(highlight.copy(alpha = 0.42f), Color.Transparent),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
            }
        NeuStyle.Sunken -> Modifier
            .clip(shape)
            .background(sunken, shape)
            .border(1.dp, shadow.copy(alpha = 0.32f), shape)
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            shadow.copy(alpha = 0.30f),
                            Color.Transparent,
                            highlight.copy(alpha = 0.22f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
            }
        NeuStyle.Flat -> Modifier.background(surface, shape)
    },
)

/** Raised panel; becomes sunken while pressed when [onClick] is provided. */
@Composable
fun NeumorphicPanel(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.panel,
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

/** Raised card (20-dp radius); pressable variant supported. */
@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Space.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    NeumorphicPanel(modifier, shape = Radius.card, contentPadding = contentPadding, onClick = onClick, content = content)
}

/** Soft raised button that presses from raised → sunken. */
@Composable
fun NeumorphicButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val c = FreyaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val style = if (pressed) NeuStyle.Sunken else NeuStyle.Raised
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = Space.touch)
            .neumorphic(Radius.control, style, c.surface, c.sunken, c.neuHighlight, c.neuShadow)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val t = if (enabled) tint else tint.copy(alpha = 0.4f)
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = t, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = t)
    }
}

/** Sunken text field: a [BasicTextField] inside an inset neumorphic well. */
@Composable
fun NeumorphicField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val c = FreyaTheme.colors
    Box(
        modifier = modifier
            .neumorphic(Radius.control, NeuStyle.Sunken, c.surface, c.sunken, c.neuHighlight, c.neuShadow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = c.textMuted,
                maxLines = 1,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(c.cyan),
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
