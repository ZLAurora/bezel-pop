@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalTextApi::class)

package com.bezelpop

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils

// ======================= fonts =======================

/** Archivo is a variable font (weight + width axes). One entry per weight we use. */
fun archivoFamily(width: Float): FontFamily = FontFamily(
    listOf(300, 400, 500, 600, 700).map { w ->
        Font(
            R.font.archivo, FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.width(width))
        )
    }
)

private fun Typography.withFont(ff: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = ff), displayMedium = displayMedium.copy(fontFamily = ff),
    displaySmall = displaySmall.copy(fontFamily = ff), headlineLarge = headlineLarge.copy(fontFamily = ff),
    headlineMedium = headlineMedium.copy(fontFamily = ff), headlineSmall = headlineSmall.copy(fontFamily = ff),
    titleLarge = titleLarge.copy(fontFamily = ff, fontWeight = FontWeight.Light),
    titleMedium = titleMedium.copy(fontFamily = ff), titleSmall = titleSmall.copy(fontFamily = ff),
    bodyLarge = bodyLarge.copy(fontFamily = ff), bodyMedium = bodyMedium.copy(fontFamily = ff),
    bodySmall = bodySmall.copy(fontFamily = ff), labelLarge = labelLarge.copy(fontFamily = ff),
    labelMedium = labelMedium.copy(fontFamily = ff), labelSmall = labelSmall.copy(fontFamily = ff)
)

// ======================= colours =======================

fun hsl(h: Float, s: Float, l: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, (s / 100f).coerceIn(0f, 1f), (l / 100f).coerceIn(0f, 1f))))

fun hueOf(argb: Int): Float {
    val a = FloatArray(3)
    ColorUtils.colorToHSL(argb, a)
    return a[0]
}

/** Vivid accents used by gradients (title, buttons, bar). Everything stays neutral in grey-scale mode. */
class Accents(val c1: Color, val c2: Color, val c3: Color, val ink: Color, val bar: Color, val barBtn: Color)

val LocalAccents = staticCompositionLocalOf { Accents(Color.Red, Color.Green, Color.Yellow, Color.White, Color.Red, Color.Black) }

private fun accentsFor(h: Float, dark: Boolean, grey: Boolean): Accents =
    if (grey) {
        if (dark) Accents(hsl(0f, 0f, 92f), hsl(0f, 0f, 55f), hsl(0f, 0f, 74f), Color.White, hsl(0f, 0f, 82f), hsl(0f, 0f, 8f))
        else Accents(hsl(0f, 0f, 16f), hsl(0f, 0f, 55f), hsl(0f, 0f, 34f), Color.Black, hsl(0f, 0f, 62f), hsl(0f, 0f, 9f))
    } else Accents(
        hsl(h, 100f, if (dark) 64f else 52f), hsl(h + 125f, 95f, if (dark) 62f else 44f), hsl(h + 60f, 95f, if (dark) 64f else 48f),
        if (dark) Color.White else Color.Black, hsl(h, if (dark) 95f else 80f, if (dark) 76f else 68f),
        hsl(h, if (dark) 45f else 50f, if (dark) 7f else 9f)
    )

private fun buildScheme(h: Float, dark: Boolean, grey: Boolean): ColorScheme {
    val k = if (grey) 0f else 1f
    fun c(s: Float, l: Float) = hsl(h, s * k, l)
    return if (dark) darkColorScheme(
        primary = c(80f, 80f), onPrimary = c(50f, 12f), primaryContainer = c(31f, 26f), onPrimaryContainer = c(12f, 93f),
        secondary = c(40f, 75f), onSecondary = c(40f, 12f), secondaryContainer = c(31f, 26f), onSecondaryContainer = c(12f, 93f),
        tertiary = c(50f, 75f), background = c(17f, 8f), onBackground = c(12f, 93f), surface = c(17f, 8f), onSurface = c(12f, 93f),
        surfaceVariant = c(17f, 22f), onSurfaceVariant = c(8f, 70f), outline = c(10f, 50f), outlineVariant = c(10f, 30f),
        surfaceContainerLowest = c(17f, 6f), surfaceContainerLow = c(17f, 10f), surfaceContainer = c(17f, 13f),
        surfaceContainerHigh = c(17f, 16f), surfaceContainerHighest = c(17f, 19f)
    ) else lightColorScheme(
        primary = c(60f, 38f), onPrimary = Color.White, primaryContainer = c(66f, 87f), onPrimaryContainer = c(20f, 10f),
        secondary = c(40f, 40f), onSecondary = Color.White, secondaryContainer = c(66f, 87f), onSecondaryContainer = c(20f, 10f),
        tertiary = c(50f, 40f), background = c(30f, 97f), onBackground = c(20f, 10f), surface = c(30f, 97f), onSurface = c(20f, 10f),
        surfaceVariant = c(31f, 88f), onSurfaceVariant = c(10f, 36f), outline = c(10f, 60f), outlineVariant = c(10f, 80f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = c(31f, 94f), surfaceContainer = c(31f, 92f),
        surfaceContainerHigh = c(31f, 89f), surfaceContainerHighest = c(31f, 86f)
    )
}

@Composable
fun BezelTheme(p: UiPrefs, content: @Composable () -> Unit) {
    val sysDark = isSystemInDarkTheme()
    val dark = when (p.appearance) { "l" -> false; "d" -> true; else -> sysDark }
    val ctx = LocalContext.current
    val scheme: ColorScheme
    val hue: Float
    if (p.grey) {
        scheme = buildScheme(0f, dark, true); hue = 0f
    } else if (p.accent != null) {
        hue = hueOf(p.accent!!); scheme = buildScheme(hue, dark, false)
    } else if (Build.VERSION.SDK_INT >= 31) {
        scheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        hue = hueOf(scheme.primary.toArgb())
    } else {
        hue = 8f; scheme = buildScheme(hue, dark, false)
    }
    val acc = accentsFor(hue, dark, p.grey)
    val type = remember { Typography().withFont(archivoFamily(100f)) }
    CompositionLocalProvider(LocalAccents provides acc) {
        MaterialTheme(
            colorScheme = scheme, typography = type,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(16.dp),
                medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)
            ),
            content = content
        )
    }
}

// ======================= background =======================

fun Modifier.dotGrid(color: Color, spacing: Dp = 18.dp): Modifier = drawWithCache {
    val s = spacing.toPx()
    val pts = ArrayList<Offset>()
    var y = s / 2f
    while (y < size.height) {
        var x = s / 2f
        while (x < size.width) {
            pts.add(Offset(x, y))
            x += s
        }
        y += s
    }
    onDrawBehind { drawPoints(pts, PointMode.Points, color, strokeWidth = 2.6f, cap = StrokeCap.Round) }
}

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.background).dotGrid(cs.onBackground.copy(alpha = 0.10f))) { content() }
}

// ======================= text =======================

/** Big gradient title: soft blend of two vivid colours + white/black, with a little grain. */
@Composable
fun GradientTitle(text: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val a = LocalAccents.current
    val ff = remember { archivoFamily(115f) }
    Text(
        text, fontFamily = ff, fontWeight = FontWeight.Light, fontSize = fontSize, letterSpacing = (-1).sp,
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val rnd = java.util.Random(7)
                val dots = List(900) { Offset(rnd.nextFloat() * w, rnd.nextFloat() * h) }
                onDrawWithContent {
                    drawContent()
                    drawRect(a.c3, blendMode = BlendMode.SrcIn)
                    drawRect(Brush.radialGradient(listOf(a.c1, Color.Transparent), Offset(w * 0.08f, h * 0.25f), w * 0.6f), blendMode = BlendMode.SrcAtop)
                    drawRect(Brush.radialGradient(listOf(a.ink, Color.Transparent), Offset(w * 0.52f, h * 0.9f), w * 0.4f), blendMode = BlendMode.SrcAtop)
                    drawRect(Brush.radialGradient(listOf(a.c2, Color.Transparent), Offset(w, h * 0.3f), w * 0.55f), blendMode = BlendMode.SrcAtop)
                    drawPoints(dots, PointMode.Points, a.ink.copy(alpha = 0.17f), strokeWidth = 1.6f, blendMode = BlendMode.SrcAtop)
                }
            }
    )
}

@Composable
private fun SectionTitle(title: String) {
    val cs = MaterialTheme.colorScheme
    val a = LocalAccents.current
    val ff = remember { archivoFamily(100f) }
    val b = Brush.horizontalGradient(listOf(cs.primary, lerp(cs.primary, a.c2, 0.42f)))
    Text(
        title, fontFamily = ff, fontWeight = FontWeight.Light, fontSize = 22.sp,
        modifier = Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
            drawContent()
            drawRect(b, blendMode = BlendMode.SrcIn)
        }
    )
}

// ======================= cards + buttons =======================

/** Card with a faint tint toward the accent. [lit] lights its outline (used when its hardware button is pressed). */
@Composable
fun Section(title: String, lit: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val a = LocalAccents.current
    val glow by animateFloatAsState(if (lit) 1f else 0f, spring(stiffness = 700f), label = "lit")
    val shape = RoundedCornerShape(30.dp)
    val bg = Brush.linearGradient(listOf(lerp(cs.surfaceContainer, a.c1, 0.06f), cs.surfaceContainer, cs.surfaceContainer))
    Box(
        Modifier.fillMaxWidth()
            .shadow((16f * glow).dp, shape, ambientColor = a.c1, spotColor = a.c1)
            .clip(shape).background(bg)
            .border((0.5f + 2.5f * glow).dp, Brush.linearGradient(listOf(a.c1.copy(alpha = glow), a.c2.copy(alpha = glow))), shape)
    ) {
        CompositionLocalProvider(LocalContentColor provides cs.onSurface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(title)
                content()
            }
        }
    }
}

/** Gradient pill button with a very subtle press scale. */
@Composable
fun GBtn(
    onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true,
    pad: Dp = 18.dp, onPress: (() -> Unit)? = null, onRelease: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val a = LocalAccents.current
    val src = remember { MutableInteractionSource() }
    val srcPressed by src.collectIsPressedAsState()
    var holding by remember { androidx.compose.runtime.mutableStateOf(false) }
    val sc by animateFloatAsState(if (srcPressed || holding) 0.985f else 1f, spring(Spring.DampingRatioMediumBouncy, 500f), label = "p")
    val brush = if (primary) Brush.linearGradient(listOf(lerp(cs.primary, Color.White, 0.15f), lerp(cs.primary, a.c2, 0.32f)))
    else Brush.linearGradient(listOf(lerp(cs.primaryContainer, a.c1, 0.15f), lerp(cs.primaryContainer, a.c2, 0.10f)))
    val fg = if (primary) cs.onPrimary else cs.onSurface
    Row(
        modifier.graphicsLayer { scaleX = sc; scaleY = sc; alpha = if (enabled) 1f else 0.45f }
            .clip(CircleShape).background(brush)
            .then(
                if (onPress != null) Modifier.pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        holding = true
                        onPress()
                        tryAwaitRelease()
                        holding = false
                        onRelease?.invoke()
                    })
                } else Modifier.clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            )
            .padding(horizontal = pad, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) { CompositionLocalProvider(LocalContentColor provides fg) { content() } }
}

// ======================= bottom bar =======================

enum class Ic { UNDO, REDO, PLUS, PLAY }

private fun iconPath(ic: Ic, s: Float): Path = Path().apply {
    when (ic) {
        Ic.UNDO -> {
            moveTo(9f * s, 14f * s); lineTo(4f * s, 9f * s); lineTo(9f * s, 4f * s)
            moveTo(4f * s, 9f * s); lineTo(14f * s, 9f * s)
            arcTo(Rect(8f * s, 9f * s, 20f * s, 21f * s), -90f, 180f, false); lineTo(11f * s, 21f * s)
        }
        Ic.REDO -> {
            moveTo(15f * s, 14f * s); lineTo(20f * s, 9f * s); lineTo(15f * s, 4f * s)
            moveTo(20f * s, 9f * s); lineTo(10f * s, 9f * s)
            arcTo(Rect(4f * s, 9f * s, 16f * s, 21f * s), -90f, -180f, false); lineTo(13f * s, 21f * s)
        }
        Ic.PLUS -> {
            moveTo(12f * s, 5f * s); lineTo(12f * s, 19f * s); moveTo(5f * s, 12f * s); lineTo(19f * s, 12f * s)
        }
        Ic.PLAY -> {
            moveTo(7f * s, 5f * s); lineTo(7f * s, 19f * s); lineTo(19f * s, 12f * s); close()
        }
    }
}

@Composable
fun BarBtn(ic: Ic, enabled: Boolean = true, onClick: () -> Unit) {
    val a = LocalAccents.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.97f else 1f, spring(Spring.DampingRatioMediumBouncy, 500f), label = "b")
    Box(
        Modifier.size(50.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape)
            .background(Brush.verticalGradient(listOf(lerp(a.barBtn, Color.White, 0.12f), a.barBtn)))
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp).graphicsLayer { alpha = if (enabled) 1f else 0.4f }) {
            val s = size.width / 24f
            drawPath(iconPath(ic, s), a.bar, style = Stroke(2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun BottomBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val a = LocalAccents.current
    Row(
        modifier.shadow(10.dp, CircleShape).clip(CircleShape)
            .background(Brush.linearGradient(listOf(lerp(a.bar, Color.White, 0.12f), lerp(a.bar, a.c1, 0.30f))))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), content = content
    )
}
