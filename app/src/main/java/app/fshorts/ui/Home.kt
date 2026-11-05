package app.fshorts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import app.fshorts.Protection
import app.fshorts.FShortsApp
import app.fshorts.AppLanguage
import app.fshorts.R
import android.content.res.Resources
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Was Startseite und Einstellungen anzeigen. */
class HomeState(
    val enabled: Boolean,
    val shortsClosed: Int,
    /** Abo/Testzeit oder Gratis-Code: nur dann arbeitet die App. */
    val isPro: Boolean,
    /** Freigeschaltet per Code (kein Google-Play-Abo). */
    val promo: Boolean,
    val themeMode: Int,
    val version: String,
    /** Schutz vom Nutzer pausiert: Shorts laufen, unsere Leiste bleibt. */
    val paused: Boolean = false,
    /** Ab wann der Schutz ausgeschaltet werden darf (Uhrzeit ms, 0 = keine Wartezeit läuft). */
    val unlockAt: Long = 0L,
    /** Jetzt (Uhrzeit ms), sekündlich aktualisiert für den Countdown. */
    val now: Long = 0L,
    /** Wartezeit fürs nächste Ausschalten in Minuten. */
    val nextWaitMinutes: Int = 10,
    /** Seit wann die App benutzt wird (Uhrzeit ms), für die Tages-Sprüche. */
    val startedAt: Long = 0L,
    /** Test-Code aktiv: Wartezeit nur 5 Sekunden. */
    val fastTimer: Boolean = false,
    /** Kennung bei Superwall, für Auskunft/Löschung per E-Mail (Einstellungen). */
    val supportId: String = "",
)

class HomeActions(
    val turnOn: () -> Unit,
    val openYouTube: () -> Unit,
    val setThemeMode: (Int) -> Unit,
    /** Unsere Superwall-Paywall. */
    val showPaywall: () -> Unit,
    /** Pro unten in der Leiste: mit Abo Abo verwalten (Jahresabo, kündigen), sonst Paywall. */
    val proTab: () -> Unit,
    val restore: () -> Unit,
    val manageSubscription: () -> Unit,
    val rateApp: () -> Unit,
    /** Code einlösen, gibt die Meldung für den Nutzer zurück. */
    val redeem: (String) -> String,
    val restartSetup: () -> Unit,
    /** Wartezeit fürs Ausschalten starten bzw. abbrechen. */
    val startUnlockWait: () -> Unit = {},
    val cancelUnlockWait: () -> Unit = {},
    /** Nach der Wartezeit: Schutz pausieren. */
    val pauseProtection: () -> Unit = {},
    /** Wieder einschalten, mit der Wartezeit fürs nächste Mal (Minuten). */
    val resumeProtection: (Int) -> Unit = {},
)

private enum class Screen { Home, Settings, Privacy }

/** Leiste unten: Status, Settings, Pro. Pro ist keine Seite, sondern öffnet immer unsere Superwall-Paywall. */
@Composable
fun HomeApp(state: HomeState, actions: HomeActions) {
    // Saveable: Sprachwechsel baut die Seite neu auf, man bleibt in den Einstellungen.
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen == Screen.Privacy) Screen.Settings else Screen.Home
    }
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(if (p.dark) Brand.strongGradient else p.background),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen",
            ) { s ->
                when (s) {
                    Screen.Home -> HomeScreen(state, actions)
                    Screen.Settings -> SettingsScreen(state, actions, onPrivacy = { screen = Screen.Privacy })
                    Screen.Privacy -> PrivacyScreen(onBack = { screen = Screen.Settings })
                }
            }
        }
        BottomNav(
            selected = if (screen == Screen.Home) 0 else 1,
            onHome = { screen = Screen.Home },
            onSettings = { screen = Screen.Settings },
            onPro = actions.proTab,
        )
    }
}

@Composable
private fun BottomNav(selected: Int, onHome: () -> Unit, onSettings: () -> Unit, onPro: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (p.dark) Color.Black.copy(alpha = 0.55f) else Color.White)
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItem(Icons.Outlined.Shield, stringResource(R.string.nav_status), selected == 0, onHome)
        NavItem(Icons.Outlined.Tune, stringResource(R.string.nav_settings), selected == 1, onSettings)
        NavItem(Icons.Outlined.WorkspacePremium, stringResource(R.string.nav_pro), false, onPro)
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) p.text.copy(alpha = 0.14f) else Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 5.dp),
        ) {
            Icon(icon, label, tint = if (selected) p.text else p.subtext, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            color = if (selected) p.text else p.subtext,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private enum class ProtectionDialog { Quote, Waiting, ConfirmOff, Relock }

@Composable
private fun HomeScreen(state: HomeState, actions: HomeActions) {
    val p = LocalPalette.current
    var dialog by remember { mutableStateOf<ProtectionDialog?>(null) }
    val waiting = !state.paused && state.unlockAt > 0L
    val remainingMs = (state.unlockAt - state.now).coerceAtLeast(0L)
    val readyToTurnOff = waiting && remainingMs == 0L
    // Arbeitet die App gerade? Bedienungshilfe an, Abo/Testzeit/Code, nicht pausiert.
    val active = state.enabled && state.isPro && !state.paused
    val res = LocalContext.current.resources
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandLogo(34.dp)
            Spacer(Modifier.width(10.dp))
            BrandName(22.sp, p.text)
        }
        Column(
            // Kein Scrollen: Alles passt auf eine Seite, Abstände wachsen mit dem Platz.
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.6f))
            // Nur über das Schild lässt sich der Schutz ausschalten (mit Spruch und Wartezeit).
            StatusOrb(
                active = active,
                waiting = waiting && !readyToTurnOff,
                onClick = {
                    dialog = when {
                        !state.enabled -> null
                        state.paused -> ProtectionDialog.Relock
                        readyToTurnOff -> ProtectionDialog.ConfirmOff
                        waiting -> ProtectionDialog.Waiting
                        else -> ProtectionDialog.Quote
                    }
                    if (!state.enabled) actions.turnOn()
                },
            )
            Spacer(Modifier.height(22.dp))
            val accent = when {
                active && waiting && !readyToTurnOff -> Brand.Amber
                active -> Brand.Green
                else -> Brand.Red
            }
            Text(stringResource(R.string.protection), style = headline(40.sp), color = p.text)
            Text(
                when {
                    state.paused -> stringResource(R.string.protection_paused)
                    active -> stringResource(R.string.protection_on)
                    else -> stringResource(R.string.protection_off)
                },
                style = headline(40.sp),
                color = accent,
            )
            Spacer(Modifier.height(8.dp))
            if (readyToTurnOff) {
                // Wartezeit abgesessen: gross, ohne "Keep protection on" darunter.
                Text(
                    stringResource(R.string.status_ready),
                    style = headline(24.sp),
                    color = p.text,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    when {
                        !state.enabled -> stringResource(R.string.status_enable)
                        state.paused -> stringResource(R.string.status_paused)
                        waiting -> stringResource(R.string.status_wait, countdown(remainingMs))
                        else -> stringResource(R.string.status_free)
                    },
                    color = p.subtext,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (waiting && !readyToTurnOff) {
                Text(
                    stringResource(R.string.keep_on),
                    color = p.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(CircleShape)
                        .clickable(onClick = actions.cancelUnlockWait)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.weight(0.6f))
            // Zahlen: geschlossene Shorts (zählt der Dienst mit) und die daraus geschätzte Zeit.
            GlassCard(padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${state.shortsClosed}", style = headline(38.sp), color = p.text)
                        Text(stringResource(R.string.shorts_closed), color = p.subtext, fontSize = 15.sp)
                    }
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(Brand.Red.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        BrandLogo(26.dp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.cardBorder))
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, tint = p.text, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(timeSaved(res, state.shortsClosed), style = headline(20.sp), color = p.text)
                        Text(stringResource(R.string.time_saved), color = p.subtext, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.weight(0.4f))
        }
        if (state.enabled) {
            MainButton(stringResource(R.string.open_youtube), actions.openYouTube, Icons.AutoMirrored.Outlined.OpenInNew)
        } else {
            MainButton(stringResource(R.string.turn_on_protection), actions.turnOn)
        }
        Spacer(Modifier.height(16.dp))
    }

    when (dialog) {
        ProtectionDialog.Quote -> QuoteDialog(
            stat = progressLine(res, state.shortsClosed, state.startedAt, state.now),
            waitLabel = if (state.fastTimer) {
                stringResource(R.string.dur_seconds, 5)
            } else {
                stringResource(R.string.dur_minutes, state.nextWaitMinutes)
            },
            onKeep = { dialog = null },
            onTurnOff = {
                dialog = null
                actions.startUnlockWait()
            },
        )
        ProtectionDialog.Waiting -> InfoDialog(
            title = stringResource(R.string.not_yet),
            text = stringResource(R.string.waiting_text, countdown(remainingMs)),
            confirm = stringResource(R.string.keep_on),
            onConfirm = {
                dialog = null
                actions.cancelUnlockWait()
            },
            dismiss = stringResource(R.string.ok),
            onDismiss = { dialog = null },
        )
        ProtectionDialog.ConfirmOff -> ProtectionSheet(
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
        ProtectionDialog.Relock -> RelockDialog(
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

/** Sprüche, die vom Ausschalten abhalten sollen (`shorts_quotes` in den Sprachdateien). */
@Composable
private fun randomQuote(): String {
    val quotes = stringArrayResource(R.array.shorts_quotes)
    val index = remember { quotes.indices.random() }
    return quotes[index]
}

/** "Das wirfst du weg": Tag seit Beginn plus eigene Zahlen im Ausschalt-Fenster. */
private fun progressLine(res: Resources, shortsClosed: Int, startedAt: Long, now: Long): String {
    val ref = if (now > 0L) now else System.currentTimeMillis()
    val day = if (startedAt <= 0L) 1 else ((ref - startedAt).coerceAtLeast(0L) / 86_400_000L + 1).toInt()
    val dayLine = when {
        day <= 1 -> res.getString(R.string.day_1)
        day == 2 -> res.getString(R.string.day_2)
        day == 3 -> res.getString(R.string.day_3)
        day == 4 -> res.getString(R.string.day_4)
        day == 5 -> res.getString(R.string.day_5)
        day == 6 -> res.getString(R.string.day_6)
        day < 14 -> res.getString(R.string.day_week, day)
        day < 30 -> res.getString(R.string.day_two_weeks, day)
        day < 60 -> res.getString(R.string.day_month, day)
        day < 180 -> res.getString(R.string.day_months, day / 30)
        day < 365 -> res.getString(R.string.day_half_year)
        else -> res.getString(R.string.day_year)
    }
    if (shortsClosed <= 0) return dayLine
    val blocked = res.getQuantityString(R.plurals.progress_blocked, shortsClosed, shortsClosed, duration(res, shortsClosed))
    return "$dayLine $blocked"
}

private fun countdown(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun QuoteDialog(stat: String, waitLabel: String, onKeep: () -> Unit, onTurnOff: () -> Unit) {
    ProtectionSheet(
        kicker = stringResource(R.string.wait_kicker),
        quote = randomQuote(),
        stat = stat,
        note = stringResource(R.string.quote_note, waitLabel),
        primary = stringResource(R.string.keep_on),
        onPrimary = onKeep,
        secondary = stringResource(R.string.start_timer, waitLabel),
        onSecondary = onTurnOff,
        onDismiss = onKeep,
    )
}

/**
 * Grosses Fenster im Paywall-Stil: Spruch gross in der Mitte, "Schutz anlassen" als weisser Hauptknopf,
 * das Ausschalten nur klein und grau darunter.
 */
@Composable
private fun ProtectionSheet(
    kicker: String,
    quote: String,
    note: String,
    stat: String? = null,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Brand.strongGradient)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.GppGood, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text(kicker, style = headline(22.sp), color = Color.White.copy(alpha = 0.55f))
            Spacer(Modifier.height(6.dp))
            Text(quote, style = headline(34.sp), color = Color.White, lineHeight = 38.sp)
            if (stat != null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    stat,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.3f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(note, color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp)
            Spacer(Modifier.height(28.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(29.dp))
                    .background(Color.White)
                    .clickable(onClick = onPrimary),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(primary, color = Brand.RedText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                secondary,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onSecondary)
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun InfoDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    dismiss: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1C),
        title = { Text(title, style = headline(26.sp), color = Color.White) },
        text = { Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp) },
        confirmButton = {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(confirm, color = Brand.RedText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismiss, color = Color.White.copy(alpha = 0.6f)) }
        },
    )
}

/**
 * Wieder einschalten: Wie lange muss man nächstes Mal warten, bis man ausschalten kann? Mindestens 5 Minuten.
 * Gleicher Stil wie [ProtectionSheet]; kurze Wartezeiten werden bewusst madig gemacht.
 */
@Composable
private fun RelockDialog(current: Int, onClose: () -> Unit, onConfirm: (Int) -> Unit) {
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
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Brand.strongGradient)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(stringResource(R.string.welcome_back), style = headline(22.sp), color = Color.White.copy(alpha = 0.55f))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.relock_title), style = headline(32.sp), color = Color.White, lineHeight = 36.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.relock_text),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(18.dp))
            options.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEach { m ->
                        val on = selected == m
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) Color.White else Color.Black.copy(alpha = 0.3f))
                                .clickable { selected = m }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (m >= 60) stringResource(R.string.dur_hours, m / 60) else stringResource(R.string.dur_minutes, m),
                                color = if (on) Brand.RedText else Color.White,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 15.sp,
                            )
                        }
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
                placeholder = { Text(stringResource(R.string.custom_minutes), color = Color.White.copy(alpha = 0.5f)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    cursorColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            val hint = when {
                selected < 0 && custom.isNotEmpty() && !valid ->
                    stringResource(R.string.at_least_minutes, Protection.MIN_WAIT_MINUTES)
                else -> warning
            }
            if (hint != null) {
                Text(
                    hint,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(29.dp))
                    .background(if (valid) Color.White else Color.White.copy(alpha = 0.35f))
                    .clickable(enabled = valid) { minutes?.let(onConfirm) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.turn_back_on), color = Brand.RedText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.keep_shorts_on),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(12.dp),
            )
        }
    }
}

/** Grosser Kreis mit Schild: grün (an), orange (Wartezeit läuft), rot (aus). Antippen = Schutz-Einstellungen. */
@Composable
private fun StatusOrb(active: Boolean, waiting: Boolean, onClick: () -> Unit) {
    val accent = when {
        active && waiting -> Brand.Amber
        active -> Brand.Green
        else -> Brand.Red
    }
    val pulse = rememberInfiniteTransition(label = "orb")
    val glow by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "glow",
    )
    Box(
        Modifier
            .size(196.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(196.dp)) {
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = glow), Color.Transparent)),
                radius = size.minDimension / 2,
            )
            drawCircle(accent.copy(alpha = 0.55f), radius = size.minDimension * 0.34f, style = Stroke(2.dp.toPx()))
        }
        Box(
            Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.7f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (active) Icons.Outlined.GppGood else Icons.Outlined.GppBad,
                stringResource(R.string.protection),
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

/** Schätzung: Wer in einem Short landet, bleibt im Schnitt ein paar Minuten hängen. */
private fun duration(res: Resources, shortsClosed: Int): String {
    val minutes = shortsClosed * 3 / 2
    return if (minutes >= 60) {
        res.getString(R.string.dur_hours_minutes, minutes / 60, minutes % 60)
    } else {
        res.getString(R.string.dur_minutes, minutes)
    }
}

private fun timeSaved(res: Resources, shortsClosed: Int): String =
    res.getString(R.string.approx, duration(res, shortsClosed))

@Composable
private fun ProPill() {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, null, tint = Brand.RedText, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text("PRO", color = Brand.RedText, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

/** Dunkel: weiss mit roter Schrift wie in der Paywall. Hell: rot mit weisser Schrift. */
@Composable
private fun MainButton(text: String, onClick: () -> Unit, icon: ImageVector? = null) {
    val p = LocalPalette.current
    val bg = if (p.dark) Color.White else Brand.Red
    val fg = if (p.dark) Brand.RedText else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(bg)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (icon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(icon, null, tint = fg)
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(p.card)
            .border(1.dp, p.cardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = p.text, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)
        Spacer(Modifier.width(14.dp))
        Text(title, style = headline(32.sp), color = p.text)
    }
}

@Composable
private fun SectionLabel(text: String) {
    val p = LocalPalette.current
    Text(
        text,
        color = p.subtext,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsScreen(state: HomeState, actions: HomeActions, onPrivacy: () -> Unit) {
    val p = LocalPalette.current
    var redeemOpen by remember { mutableStateOf(false) }
    var languageOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = headline(36.sp), color = p.text, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

        SectionLabel(stringResource(R.string.section_subscription))
        if (state.isPro) {
            GlassCard(padding = 0.dp) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandLogo(44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("F*ck Shorts Pro", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(if (state.promo) R.string.pro_unlocked_code else R.string.pro_active),
                            color = Brand.Green,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (!state.promo) {
                    Divider()
                    LinkRow(
                        Icons.AutoMirrored.Outlined.OpenInNew,
                        stringResource(R.string.manage_subscription),
                        stringResource(R.string.manage_subscription_sub),
                        actions.manageSubscription,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassCard(padding = 0.dp) {
            LinkRow(Icons.Outlined.Redeem, stringResource(R.string.redeem_code), null) { redeemOpen = true }
            Divider()
            LinkRow(Icons.Outlined.Refresh, stringResource(R.string.restore_purchases), null, actions.restore)
        }

        SectionLabel(stringResource(R.string.section_appearance))
        GlassCard(padding = 14.dp) {
            Segmented(
                listOf(
                    stringResource(R.string.theme_system),
                    stringResource(R.string.theme_dark),
                    stringResource(R.string.theme_light),
                ),
                state.themeMode,
                actions.setThemeMode,
            )
        }

        SectionLabel(stringResource(R.string.section_language))
        GlassCard(padding = 0.dp) {
            val context = LocalContext.current
            val lang = remember { AppLanguage.current(context) }
            LinkRow(Icons.Outlined.Language, stringResource(R.string.language), "${lang.flag}  ${lang.name}") {
                languageOpen = true
            }
        }

        SectionLabel(stringResource(R.string.section_about))
        GlassCard(padding = 0.dp) {
            LinkRow(Icons.Outlined.StarOutline, stringResource(R.string.rate_app), null, actions.rateApp)
            Divider()
            LinkRow(Icons.Outlined.Description, stringResource(R.string.privacy_permissions), null, onPrivacy)
            Divider()
            val uriHandler = LocalUriHandler.current
            LinkRow(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.privacy_policy), null) {
                runCatching { uriHandler.openUri(FShortsApp.PRIVACY_POLICY_URL) }
            }
            Divider()
            // Für Auskunft/Löschung bei Superwall: Nutzer schicken diese Kennung per E-Mail mit.
            val context = LocalContext.current
            val copied = stringResource(R.string.support_id_copied)
            LinkRow(
                Icons.Outlined.ContentCopy,
                stringResource(R.string.support_id),
                "${state.supportId.ifEmpty { "–" }}\n${stringResource(R.string.support_id_hint)}",
            ) {
                if (state.supportId.isNotEmpty()) {
                    runCatching {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("Support ID", state.supportId))
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Box(Modifier.align(Alignment.CenterHorizontally)) { BrandLogo(40.dp) }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.version, state.version) + "\n" + stringResource(R.string.not_affiliated),
            color = p.subtext,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        // Ganz unten: Einrichtung nochmals von vorne.
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .border(1.dp, p.cardBorder, RoundedCornerShape(26.dp))
                .clickable(onClick = actions.restartSetup),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.RestartAlt, null, tint = p.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.run_setup_again), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
    }
    if (redeemOpen) RedeemDialog(actions.redeem, onClose = { redeemOpen = false })
    if (languageOpen) LanguageSheet(dark = p.dark, onClose = { languageOpen = false })
}

/** Gratis-Code eingeben (auch im Onboarding). */
@Composable
fun RedeemDialog(redeem: (String) -> String, onClose: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF1A1A1C),
        title = { Text(stringResource(R.string.redeem_code), color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        message = null
                    },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.code_hint)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        cursorColor = Color.White,
                    ),
                )
                message?.let { Text(it, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 10.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { message = redeem(code) }) {
                Text(stringResource(R.string.redeem), color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.close), color = Color.White.copy(alpha = 0.7f)) }
        },
    )
}

@Composable
private fun Divider() {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().padding(start = 54.dp).height(1.dp).background(p.cardBorder))
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.text, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = p.subtext, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.subtext)
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.text.copy(alpha = 0.06f))
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        when {
                            !on -> Color.Transparent
                            p.dark -> Color.White
                            else -> Brand.Red
                        },
                    )
                    .clickable { onSelect(i) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = when {
                        on && p.dark -> Brand.RedText
                        on -> Color.White
                        else -> p.subtext
                    },
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/** Derselbe Hinweis wie beim Einschalten, jederzeit nachzulesen. */
@Composable
private fun PrivacyScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        ScreenHeader(stringResource(R.string.privacy), onBack)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF1A1A1C))) {
            Disclosure()
        }
        Spacer(Modifier.height(24.dp))
    }
}
