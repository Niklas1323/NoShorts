package app.fshorts.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fshorts.AppLanguage
import app.fshorts.FShortsApp
import app.fshorts.Protection
import app.fshorts.R

// -------------------------------------------------------------------------------------------------------------------
// Design V2 (17.11.2026, Wunsch von Niklas: "nicht vibe-coded, premium, minimalistisch, im Stil unserer Leiste").
// Wie YouTube selbst: flaches Schwarz (bzw. Weiss), graue Flächen ohne Rahmen, keine Verläufe und kein Leuchten,
// YouTube-Rot nur als Akzent (Hauptknopf, Auswahl). Die Leiste unten sieht aus wie unsere Leiste in YouTube.
// V1 (Home.kt) bleibt daneben erhalten; Test-Versionen können in den Einstellungen umschalten.
// -------------------------------------------------------------------------------------------------------------------

/** Farben einer Seite im Design V2 (dunkel wie YouTube #0F0F0F, hell weiss). */
data class V2Colors(
    val dark: Boolean,
    val bg: Color,
    /** Kacheln, Dialoge. */
    val surface: Color,
    /** Chips (nicht gewählt). */
    val chip: Color,
    val text: Color,
    val sub: Color,
    val line: Color,
)

private val V2Dark = V2Colors(
    dark = true,
    bg = Color(0xFF0F0F0F),
    surface = Color(0xFF1F1F1F),
    chip = Color(0xFF272727),
    text = Color(0xFFF1F1F1),
    sub = Color(0xFFAAAAAA),
    line = Color.White.copy(alpha = 0.10f),
)

private val V2Light = V2Colors(
    dark = false,
    bg = Color.White,
    surface = Color(0xFFF2F2F2),
    chip = Color(0xFFF2F2F2),
    text = Color(0xFF0F0F0F),
    sub = Color(0xFF606060),
    line = Color.Black.copy(alpha = 0.10f),
)

/** Akzentfarben V2: YouTube-Rot, Grün (Schutz an), Orange (Wartezeit). */
object V2 {
    val Red = Color(0xFFFF0033)
    val Green = Color(0xFF2BA640)
    val Amber = Color(0xFFF5A000)
}

@Composable
private fun v2(): V2Colors = if (LocalPalette.current.dark) V2Dark else V2Light

private enum class ScreenV2 { Home, Settings, Privacy }

/** Startseite, Einstellungen und Datenschutz im Design V2. Pro (unten rechts) öffnet die Paywall bzw. das Abo. */
@Composable
fun HomeAppV2(state: HomeState, actions: HomeActions, onDesignV1: (() -> Unit)? = null) {
    var screen by rememberSaveable { mutableStateOf(ScreenV2.Home) }
    BackHandler(enabled = screen != ScreenV2.Home) {
        screen = if (screen == ScreenV2.Privacy) ScreenV2.Settings else ScreenV2.Home
    }
    val c = v2()
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "screen",
            ) { s ->
                when (s) {
                    ScreenV2.Home -> HomeScreenV2(state, actions)
                    ScreenV2.Settings -> SettingsScreenV2(state, actions, onPrivacy = { screen = ScreenV2.Privacy }, onDesignV1)
                    ScreenV2.Privacy -> PrivacyScreenV2(onBack = { screen = ScreenV2.Settings })
                }
            }
        }
        NavBarV2(
            selected = if (screen == ScreenV2.Home) 0 else 1,
            onHome = { screen = ScreenV2.Home },
            onSettings = { screen = ScreenV2.Settings },
            onPro = actions.proTab,
        )
    }
}

/** Leiste unten wie unsere Leiste in YouTube: Symbol (gewählt gefüllt) mit kleiner Beschriftung, feine Linie oben. */
@Composable
private fun NavBarV2(selected: Int, onHome: () -> Unit, onSettings: () -> Unit, onPro: () -> Unit) {
    val c = v2()
    Column(Modifier.fillMaxWidth().background(c.bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(60.dp)) {
            NavItemV2(Icons.Filled.Shield, Icons.Outlined.Shield, stringResource(R.string.nav_status), selected == 0, onHome)
            NavItemV2(Icons.Filled.Settings, Icons.Outlined.Settings, stringResource(R.string.nav_settings), selected == 1, onSettings)
            NavItemV2(Icons.Filled.WorkspacePremium, Icons.Outlined.WorkspacePremium, stringResource(R.string.nav_pro), false, onPro)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItemV2(
    filled: ImageVector,
    outlined: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = v2()
    Column(
        Modifier.weight(1f).fillMaxSize().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (selected) filled else outlined, label, tint = c.text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = c.text,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** "F*ck Shorts" schlicht (wie YouTubes Schriftzug oben links), rotes Sternchen. */
@Composable
private fun WordmarkV2(size: TextUnit) {
    val c = v2()
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandLogo(size.value.dp * 1.3f, background = V2.Red)
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                append("F")
                withStyle(SpanStyle(color = V2.Red)) { append("*") }
                append("ck Shorts")
            },
            color = c.text,
            fontSize = size,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        )
    }
}

private enum class DialogV2 { Quote, Waiting, ConfirmOff, Relock }

@Composable
private fun HomeScreenV2(state: HomeState, actions: HomeActions) {
    val c = v2()
    var dialog by remember { mutableStateOf<DialogV2?>(null) }
    val waiting = !state.paused && state.unlockAt > 0L
    val remainingMs = (state.unlockAt - state.now).coerceAtLeast(0L)
    val readyToTurnOff = waiting && remainingMs == 0L
    val active = state.enabled && state.isPro && !state.paused
    val res = LocalContext.current.resources
    val accent = when {
        active && waiting && !readyToTurnOff -> V2.Amber
        active -> V2.Green
        else -> V2.Red
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Wenig Platz (kleiner Bildschirm, grosse Schrift wie 130 %): alles etwas kleiner, damit es ohne Scrollen passt.
        val fontScale = LocalDensity.current.fontScale
        val dense = maxHeight < 660.dp || (fontScale >= 1.15f && maxHeight < 780.dp)
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterStart) {
                WordmarkV2(20.sp)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                // Nur über das Schild lässt sich der Schutz ausschalten (mit Spruch und Wartezeit).
                StatusEmblem(
                    accent = accent,
                    active = active,
                    size = if (dense) 150.dp else 200.dp,
                    onClick = {
                        dialog = when {
                            !state.enabled -> null
                            state.paused -> DialogV2.Relock
                            readyToTurnOff -> DialogV2.ConfirmOff
                            waiting -> DialogV2.Waiting
                            else -> DialogV2.Quote
                        }
                        if (!state.enabled) actions.turnOn()
                    },
                )
                Spacer(Modifier.height(if (dense) 12.dp else 20.dp))
                Text(
                    buildAnnotatedString {
                        append(stringResource(R.string.protection))
                        append(" ")
                        withStyle(SpanStyle(color = accent)) {
                            append(
                                when {
                                    state.paused -> stringResource(R.string.protection_paused)
                                    active -> stringResource(R.string.protection_on)
                                    else -> stringResource(R.string.protection_off)
                                },
                            )
                        }
                    },
                    color = c.text,
                    fontSize = if (dense) 26.sp else 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        readyToTurnOff -> stringResource(R.string.status_ready)
                        !state.enabled -> stringResource(R.string.status_enable)
                        state.paused -> stringResource(R.string.status_paused)
                        waiting -> stringResource(R.string.status_wait, countdown(remainingMs))
                        else -> stringResource(R.string.status_free)
                    },
                    color = if (readyToTurnOff) c.text else c.sub,
                    fontSize = 15.sp,
                    fontWeight = if (readyToTurnOff) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
                if (waiting && !readyToTurnOff) {
                    ChipV2(stringResource(R.string.keep_on), selected = true, modifier = Modifier.padding(top = 12.dp), onClick = actions.cancelUnlockWait)
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTileV2("${state.shortsClosed}", stringResource(R.string.stat_shorts), dense, Modifier.weight(1f))
                    StatTileV2(timeSaved(res, state.shortsClosed), stringResource(R.string.stat_time), dense, Modifier.weight(1f))
                }
                Spacer(Modifier.height(if (dense) 14.dp else 20.dp))
                // Leisten-Stil auf die Schnelle wählen (Wunsch von Niklas): zwei Bildchen.
                Text(
                    stringResource(R.string.bar_widget),
                    color = c.sub,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 8.dp),
                )
                BarStyleTilesV2(state.barModern, actions.setBarModern)
                Spacer(Modifier.height(if (dense) 12.dp else 18.dp))
            }
            if (state.enabled) {
                PrimaryButtonV2(stringResource(R.string.open_youtube), actions.openYouTube, Icons.AutoMirrored.Outlined.OpenInNew)
            } else {
                PrimaryButtonV2(stringResource(R.string.turn_on_protection), actions.turnOn)
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    when (dialog) {
        DialogV2.Quote -> {
            val waitLabel = if (state.fastTimer) {
                stringResource(R.string.dur_seconds, 5)
            } else {
                stringResource(R.string.dur_minutes, state.nextWaitMinutes)
            }
            ProtectionSheetV2(
                kicker = stringResource(R.string.wait_kicker),
                quote = randomQuote(),
                stat = progressLine(res, state.shortsClosed, state.startedAt, state.now),
                note = stringResource(R.string.quote_note, waitLabel),
                primary = stringResource(R.string.keep_on),
                onPrimary = { dialog = null },
                secondary = stringResource(R.string.start_timer, waitLabel),
                onSecondary = {
                    dialog = null
                    actions.startUnlockWait()
                },
                onDismiss = { dialog = null },
            )
        }
        DialogV2.Waiting -> SheetV2(onDismiss = { dialog = null }) {
            Text(stringResource(R.string.not_yet), color = v2().text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.waiting_text, countdown(remainingMs)), color = v2().sub, fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            PrimaryButtonV2(stringResource(R.string.keep_on), {
                dialog = null
                actions.cancelUnlockWait()
            })
            SecondaryButtonV2(stringResource(R.string.ok)) { dialog = null }
        }
        DialogV2.ConfirmOff -> ProtectionSheetV2(
            kicker = stringResource(R.string.last_chance),
            quote = randomQuote(),
            stat = progressLine(res, state.shortsClosed, state.startedAt, state.now),
            note = stringResource(R.string.confirm_note),
            primary = stringResource(R.string.keep_on),
            onPrimary = {
                dialog = null
                actions.cancelUnlockWait()
            },
            secondary = stringResource(R.string.turn_off_protection),
            onSecondary = {
                dialog = null
                actions.pauseProtection()
            },
            onDismiss = { dialog = null },
        )
        DialogV2.Relock -> RelockSheetV2(
            current = state.nextWaitMinutes,
            onClose = { dialog = null },
            onConfirm = { minutes ->
                dialog = null
                actions.resumeProtection(minutes)
            },
        )
        null -> Unit
    }
}

/**
 * Grosses Schild (Wunsch: grösser als in V1): flacher Kreis in der Statusfarbe mit weissem Schild. Ist der Schutz an,
 * "atmet" ein feiner Ring ruhig nach aussen – lebendig, aber ohne Leuchten.
 */
@Composable
private fun StatusEmblem(accent: Color, active: Boolean, size: Dp, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "emblem")
    val t by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "breathe",
    )
    val core = size * 0.74f
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r0 = core.toPx() / 2f
            val max = this.size.minDimension / 2f
            // Fester, ganz zarter Hof.
            drawCircle(accent.copy(alpha = 0.10f), radius = r0 + (max - r0) * 0.45f)
            if (active) {
                val r = r0 + (max - r0) * t
                drawCircle(accent.copy(alpha = 0.35f * (1f - t)), radius = r, style = Stroke(1.5.dp.toPx()))
            }
        }
        Box(
            Modifier
                .size(core)
                .clip(CircleShape)
                .background(accent)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (active) Icons.Filled.GppGood else Icons.Filled.GppBad,
                stringResource(R.string.protection),
                tint = Color.White,
                modifier = Modifier.size(core * 0.46f),
            )
        }
    }
}

@Composable
private fun StatTileV2(value: String, label: String, dense: Boolean, modifier: Modifier = Modifier) {
    val c = v2()
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .padding(horizontal = 16.dp, vertical = if (dense) 12.dp else 16.dp),
    ) {
        Text(value, color = c.text, fontSize = if (dense) 20.sp else 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, color = c.sub, fontSize = 13.sp, maxLines = 1)
    }
}

/** Zwei Kacheln mit Vorschau der Leiste (klassisch / modern). Gewählt: roter Rand, Name fett. */
@Composable
private fun BarStyleTilesV2(modern: Boolean, onSelect: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BarTileV2(false, !modern, stringResource(R.string.bar_classic), Modifier.weight(1f)) { onSelect(false) }
        BarTileV2(true, modern, stringResource(R.string.bar_modern), Modifier.weight(1f)) { onSelect(true) }
    }
}

@Composable
private fun BarTileV2(modern: Boolean, selected: Boolean, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = v2()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(c.surface)
            .border(2.dp, if (selected) V2.Red else Color.Transparent, shape)
            .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScaledBarPreview(modern = modern, dark = c.dark, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            color = if (selected) c.text else c.sub,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** Hauptknopf: YouTube-Rot, weisse Schrift. */
@Composable
private fun PrimaryButtonV2(text: String, onClick: () -> Unit, icon: ImageVector? = null, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(if (enabled) V2.Red else V2.Red.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (icon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SecondaryButtonV2(text: String, onClick: () -> Unit) {
    val c = v2()
    Text(
        text,
        color = c.sub,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    )
}

/** Chip wie YouTubes Filter oben: grau, gewählt weiss mit schwarzer Schrift (hell umgekehrt). */
@Composable
private fun ChipV2(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = v2()
    val bg = when {
        selected && c.dark -> Color(0xFFF1F1F1)
        selected -> Color(0xFF0F0F0F)
        else -> c.chip
    }
    val fg = when {
        selected && c.dark -> Color(0xFF0F0F0F)
        selected -> Color.White
        else -> c.text
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Dialog im Design V2: graue Fläche, runde Ecken, ohne Verlauf. */
@Composable
private fun SheetV2(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = v2()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (c.dark) Color(0xFF212121) else Color.White)
                .padding(horizontal = 24.dp, vertical = 26.dp),
            content = content,
        )
    }
}

/** Ausschalten: Spruch gross, "Schutz anlassen" als roter Hauptknopf, Ausschalten klein darunter. */
@Composable
private fun ProtectionSheetV2(
    kicker: String,
    quote: String,
    stat: String?,
    note: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = v2()
    SheetV2(onDismiss) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(V2.Green), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.GppGood, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(kicker, color = c.sub, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(quote, color = c.text, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
        if (stat != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                stat,
                color = c.text,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.chip)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(note, color = c.sub, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        PrimaryButtonV2(primary, onPrimary)
        SecondaryButtonV2(secondary, onSecondary)
    }
}

/** Wieder einschalten: Wartezeit fürs nächste Ausschalten wählen (mindestens 5 Minuten). */
@Composable
private fun RelockSheetV2(current: Int, onClose: () -> Unit, onConfirm: (Int) -> Unit) {
    val c = v2()
    val options = listOf(5, 10, 15, 20, 30, 45, 60, 120)
    var selected by remember { mutableStateOf(if (current in options) current else -1) }
    var custom by remember { mutableStateOf(if (current in options) "" else current.toString()) }
    val minutes = if (selected > 0) selected else custom.toIntOrNull()
    val valid = minutes != null && minutes >= Protection.MIN_WAIT_MINUTES
    val warning = when {
        minutes == null || !valid -> null
        minutes < 15 -> stringResource(R.string.relock_warn_short, minutes)
        minutes < 45 -> stringResource(R.string.relock_warn_mid, minutes)
        else -> stringResource(R.string.relock_warn_long)
    }
    SheetV2(onClose) {
        Text(stringResource(R.string.welcome_back), color = c.sub, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.relock_title), color = c.text, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.relock_text), color = c.sub, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        options.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEach { m ->
                    ChipV2(
                        if (m >= 60) stringResource(R.string.dur_hours, m / 60) else stringResource(R.string.dur_minutes, m),
                        selected = selected == m,
                        modifier = Modifier.weight(1f),
                    ) { selected = m }
                }
            }
        }
        OutlinedTextField(
            value = custom,
            onValueChange = { v ->
                custom = v.filter { it.isDigit() }.take(4)
                selected = -1
            },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.custom_minutes), color = c.sub) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            colors = v2FieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        val hint = when {
            selected < 0 && custom.isNotEmpty() && !valid -> stringResource(R.string.at_least_minutes, Protection.MIN_WAIT_MINUTES)
            else -> warning
        }
        if (hint != null) {
            Text(hint, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButtonV2(stringResource(R.string.turn_back_on), { minutes?.let(onConfirm) }, enabled = valid)
        SecondaryButtonV2(stringResource(R.string.keep_shorts_on), onClose)
    }
}

@Composable
private fun v2FieldColors() = v2().let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.text,
        unfocusedTextColor = c.text,
        focusedBorderColor = c.text,
        unfocusedBorderColor = c.line.copy(alpha = 0.3f),
        cursorColor = c.text,
    )
}

/** Gratis-Code eingeben (Design V2). */
@Composable
private fun RedeemSheetV2(redeem: (String) -> String, onClose: () -> Unit) {
    val c = v2()
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    SheetV2(onClose) {
        Text(stringResource(R.string.redeem_code), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = code,
            onValueChange = {
                code = it
                message = null
            },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.code_hint), color = c.sub) },
            shape = RoundedCornerShape(12.dp),
            colors = v2FieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        message?.let { Text(it, color = c.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(20.dp))
        PrimaryButtonV2(stringResource(R.string.redeem), { message = redeem(code) }, enabled = code.isNotBlank())
        SecondaryButtonV2(stringResource(R.string.close), onClose)
    }
}

// --------------------------------------------- Einstellungen ---------------------------------------------------------

/** Einstellungen wie bei YouTube: schlichte Liste, Abschnitt-Titel grau, keine Pfeile und Rahmen. */
@Composable
private fun SettingsScreenV2(state: HomeState, actions: HomeActions, onPrivacy: () -> Unit, onDesignV1: (() -> Unit)?) {
    val c = v2()
    var redeemOpen by remember { mutableStateOf(false) }
    var languageOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
            Text(stringResource(R.string.nav_settings), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        SectionV2(stringResource(R.string.section_subscription))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandLogo(40.dp, background = V2.Red)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("F*ck Shorts Pro", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    when {
                        !state.isPro -> stringResource(R.string.nav_pro)
                        state.promo -> stringResource(R.string.pro_unlocked_code)
                        else -> stringResource(R.string.pro_active)
                    },
                    color = if (state.isPro) V2.Green else c.sub,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        if (state.isPro && !state.promo) {
            RowV2(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.manage_subscription), actions.manageSubscription)
        }
        RowV2(Icons.Outlined.Redeem, stringResource(R.string.redeem_code)) { redeemOpen = true }
        RowV2(Icons.Outlined.Refresh, stringResource(R.string.restore_purchases), actions.restore)

        SectionV2(stringResource(R.string.section_appearance))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(R.string.theme_system, R.string.theme_dark, R.string.theme_light).forEachIndexed { i, id ->
                ChipV2(stringResource(id), selected = state.themeMode == i, modifier = Modifier.weight(1f)) { actions.setThemeMode(i) }
            }
        }

        // Sprache gleich unter dem Design (vorher eigener Abschnitt "Sprache" mit Zeile "Sprache": doppelt).
        val context = LocalContext.current
        val lang = remember { AppLanguage.current(context) }
        RowV2(Icons.Outlined.Language, stringResource(R.string.language), subtitle = "${lang.flag}  ${lang.name}") { languageOpen = true }

        SectionV2(stringResource(R.string.bar_widget))
        Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            BarStyleTilesV2(state.barModern, actions.setBarModern)
        }

        SectionV2(stringResource(R.string.section_about))
        RowV2(Icons.Outlined.StarOutline, stringResource(R.string.rate_app), actions.rateApp)
        RowV2(Icons.Outlined.Description, stringResource(R.string.privacy_permissions), onPrivacy)
        val uriHandler = LocalUriHandler.current
        RowV2(Icons.Outlined.Policy, stringResource(R.string.privacy_policy)) {
            runCatching { uriHandler.openUri(FShortsApp.PRIVACY_POLICY_URL) }
        }
        SupportIdRowV2(state.supportId)
        RowV2(Icons.Outlined.RestartAlt, stringResource(R.string.run_setup_again), actions.restartSetup)
        if (onDesignV1 != null) {
            // Nur Test-Versionen: zum alten Design (V1) zurück, zum Vergleichen.
            RowV2(Icons.Outlined.Brightness6, "Design V1 (Test)", onDesignV1)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.version, state.version) + "\n" + stringResource(R.string.not_affiliated),
            color = c.sub,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
    if (redeemOpen) RedeemSheetV2(actions.redeem, onClose = { redeemOpen = false })
    if (languageOpen) LanguageSheet(dark = c.dark, onClose = { languageOpen = false })
}

/** Abschnitt-Titel wie bei YouTube: normal geschrieben statt GROSS (die Texte sind für V1 in Grossbuchstaben). */
@Composable
private fun SectionV2(title: String) {
    val c = v2()
    val locale = androidx.compose.ui.text.intl.Locale.current.platformLocale
    val normal = if (title == title.uppercase(locale) && title.length > 1) {
        title.lowercase(locale).replaceFirstChar { it.titlecase(locale) }
            .replace(Regex("youtube", RegexOption.IGNORE_CASE), "YouTube")
    } else {
        title
    }
    Text(
        normal,
        color = c.sub,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp),
    )
}

@Composable
private fun RowV2(icon: ImageVector, title: String, onClick: () -> Unit) = RowV2(icon, title, null, onClick)

@Composable
private fun RowV2(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    val c = v2()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = c.sub, fontSize = 13.sp)
        }
    }
}

/**
 * Support-ID (Datenschutzerklärung: für Auskunft/Löschung bei Superwall nötig): zugeklappt nur der Titel, aufgeklappt
 * die Kennung klein mit "Kopieren". Vorher stand die lange Kennung samt Erklärung immer da.
 */
@Composable
private fun SupportIdRowV2(supportId: String) {
    val c = v2()
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val copied = stringResource(R.string.support_id_copied)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.SupportAgent, null, tint = c.text, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(20.dp))
            Text(stringResource(R.string.support_id), color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = c.sub)
        }
        AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(start = 64.dp, end = 20.dp, bottom = 12.dp)) {
                Text(stringResource(R.string.support_id_hint), color = c.sub, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                // Antippen kopiert (wie der Hinweis darüber sagt).
                Text(
                    supportId.ifEmpty { "–" },
                    color = c.text,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surface)
                        .clickable(enabled = supportId.isNotEmpty()) {
                            runCatching {
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("Support ID", supportId))
                                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(12.dp),
                )
            }
        }
    }
}

/** Datenschutz & Berechtigungen (derselbe Hinweis wie beim Einschalten). */
@Composable
private fun PrivacyScreenV2(onBack: () -> Unit) {
    val c = v2()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = c.text)
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.privacy), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Disclosure(textColor = c.text, background = c.surface, border = Color.Transparent)
        }
        Spacer(Modifier.height(24.dp))
    }
}
