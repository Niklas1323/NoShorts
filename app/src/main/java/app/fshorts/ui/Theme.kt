package app.fshorts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Farben im Stil der Superwall-Paywall: YouTube-Schwarz, das in ein ruhiges Rot übergeht. */
object Brand {
    val Red = Color(0xFFE5172B)
    val RedText = Color(0xFFC8161F)
    val Green = Color(0xFF2FD27A)
    /** Wartezeit fürs Ausschalten läuft. */
    val Amber = Color(0xFFFFA726)
    val Black = Color(0xFF0F0F0F)

    /** Wie der Hintergrund der Paywall (Onboarding). */
    val strongGradient = Brush.verticalGradient(
        0f to Black,
        0.28f to Color(0xFF2A080B),
        0.62f to Color(0xFF8E1219),
        1f to Color(0xFFB3161F),
    )
}

/** Farben einer Seite; im hellen Design ohne roten Verlauf. */
data class Palette(
    val dark: Boolean,
    val background: Brush,
    val card: Color,
    val cardBorder: Color,
    val text: Color,
    val subtext: Color,
    val faint: Color,
)

private val DarkPalette = Palette(
    dark = true,
    background = Brush.verticalGradient(
        0f to Brand.Black,
        0.55f to Color(0xFF160709),
        1f to Color(0xFF3B0B11),
    ),
    card = Color.White.copy(alpha = 0.06f),
    cardBorder = Color.White.copy(alpha = 0.09f),
    text = Color.White,
    subtext = Color.White.copy(alpha = 0.62f),
    faint = Color.White.copy(alpha = 0.35f),
)

private val LightPalette = Palette(
    dark = false,
    background = Brush.verticalGradient(0f to Color(0xFFF4F4F6), 1f to Color(0xFFF1ECEC)),
    card = Color.White,
    cardBorder = Color.Black.copy(alpha = 0.06f),
    text = Color(0xFF0F0F0F),
    subtext = Color(0xFF0F0F0F).copy(alpha = 0.6f),
    faint = Color(0xFF0F0F0F).copy(alpha = 0.35f),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/** themeMode: 0 = wie das Handy, 1 = dunkel, 2 = hell. */
@Composable
fun FShortsTheme(themeMode: Int, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) {
        darkColorScheme(primary = Brand.Red, onPrimary = Color.White, background = Brand.Black, surface = Color(0xFF18181A))
    } else {
        lightColorScheme(primary = Brand.Red, onPrimary = Color.White)
    }
    CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Fette Überschrift wie im Design. */
fun headline(size: TextUnit) = TextStyle(fontSize = size, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp)

/** "F*ck Shorts" mit rotem Sternchen. */
@Composable
fun BrandName(size: TextUnit, color: Color = Color.White) {
    Text(
        buildAnnotatedString {
            append("F")
            withStyle(SpanStyle(color = Brand.Red)) { append("*") }
            append("ck Shorts")
        },
        style = headline(size),
        color = color,
    )
}

/** Das Logo: ein Hochkant-Video, mit einem Schnitt durchtrennt. */
@Composable
fun BrandLogo(size: Dp, background: Color = Brand.Red, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.27f))
            .background(background),
    ) {
        Canvas(Modifier.size(size)) {
            val s = this.size.width
            val cardW = s * 0.42f
            val cardH = s * 0.60f
            val left = (s - cardW) / 2
            val top = (s - cardH) / 2
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(left, top),
                size = Size(cardW, cardH),
                cornerRadius = CornerRadius(s * 0.09f),
            )
            val from = Offset(s * 0.27f, s * 0.80f)
            val to = Offset(s * 0.75f, s * 0.22f)
            drawLine(background, from, to, strokeWidth = s * 0.15f, cap = StrokeCap.Round)
            drawLine(Color.White, from, to, strokeWidth = s * 0.065f, cap = StrokeCap.Round)
        }
    }
}

/** Karte wie in der Paywall: leicht durchsichtig, feiner Rand. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    border: Color? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.card)
            .border(1.dp, border ?: p.cardBorder, shape)
            .padding(padding),
        content = content,
    )
}
