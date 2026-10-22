package fr.horizon.tv.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDateTime

@Composable
fun HText(
    text: String,
    style: TextStyle = HType.Body,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = (if (color == Color.Unspecified) style else style.copy(color = color))
            .let { if (textAlign != null) it.copy(textAlign = textAlign) else it },
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

fun Modifier.glass(shape: Shape = RoundedCornerShape(20.dp), fill: Color = HColors.Glass): Modifier =
    this.clip(shape).background(fill).border(1.dp, HColors.GlassBorder, shape)

/**
 * Carte focalisable façon tvOS : agrandissement ressort, ombre portée et liseré blanc au focus.
 * Tout passe par graphicsLayer pour rester sur le thread de rendu (aucune recomposition à l'animation).
 */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    focusedScale: Float = 1.07f,
    background: Color = HColors.Glass,
    onFocus: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) focusedScale else 1f,
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "scale",
    )
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "glow")
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = 48f * glow
                this.shape = shape
                clip = false
                ambientShadowColor = Color.Black
                spotShadowColor = Color.Black
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clickable(interaction, indication = null, onClick = onClick)
            .clip(shape)
            .background(background)
            .border(
                BorderStroke(2.5.dp, Color.White.copy(alpha = 0.9f * glow)),
                shape,
            ),
    ) { content(focused) }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) HColors.Ink else HColors.Text
    FocusCard(
        onClick = onClick,
        modifier = modifier.height(46.dp).onFocusChanged { focused = it.isFocused },
        shape = CircleShape,
        focusedScale = 1.06f,
        background = if (primary) HColors.Accent.copy(alpha = 0.92f) else HColors.GlassStrong,
        onFocus = onFocus,
    ) { isFocused ->
        Box(
            Modifier.fillMaxSize().background(if (isFocused) Color.White else Color.Transparent)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) HIcon(icon, Modifier.size(20.dp), tint = if (primary && !isFocused) HColors.Ink else fg)
                HText(text, HType.Label.copy(color = if (primary && !isFocused) HColors.Ink else fg), maxLines = 1)
            }
        }
    }
}

@Composable
fun HIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = HColors.Text) {
    Image(icon, null, modifier, colorFilter = ColorFilter.tint(tint))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.Bottom) {
        HText(text, HType.Section)
        if (trailing != null) {
            Box(Modifier.padding(start = 12.dp, bottom = 2.dp)) { HText(trailing, HType.Caption) }
        }
    }
}

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HText(label, HType.Caption)
        Box(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                .background(if (focused) Color.White.copy(alpha = 0.22f) else HColors.Glass)
                .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else HColors.GlassBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) HText(placeholder, HType.Body, Modifier.padding(horizontal = 14.dp), color = HColors.TextFaint, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = HType.Body,
                cursorBrush = SolidColor(Color.White),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onDone = { onDone?.invoke() ?: focusManager.moveFocus(FocusDirection.Down) },
                    onSearch = { onDone?.invoke() },
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

@Composable
fun CheckDot(checked: Boolean) {
    Box(
        Modifier.size(24.dp).clip(CircleShape)
            .background(if (checked) HColors.Accent else Color.Transparent)
            .border(2.dp, if (checked) HColors.Accent else HColors.TextFaint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) HIcon(Icons.Filled.Check, Modifier.size(16.dp), tint = HColors.Ink)
    }
}

@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        val now = LocalDateTime.now()
        value = now
        delay((60 - now.second) * 1000L)
    }
}

/** Fait défiler les listes pour caler l'élément focalisé près du bord de départ (comportement tvOS). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PivotScroll(startPadding: Dp, content: @Composable () -> Unit) {
    val pad = with(LocalDensity.current) { startPadding.toPx() }
    val spec = remember(pad) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val end = offset + size
                // Déjà entièrement visible : on ne bouge pas, pas de saut inutile.
                return if (offset >= pad && end <= containerSize - pad) 0f else offset - pad
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.restoreFocus(): Modifier = this.focusRestorer()

val ScreenPadding = PaddingValues(horizontal = 48.dp)
val RowGap = Arrangement.spacedBy(20.dp)

@Composable
fun Scrim(modifier: Modifier = Modifier, colors: List<Color>) {
    Box(modifier.background(Brush.verticalGradient(colors)))
}
