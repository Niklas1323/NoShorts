package app.fshorts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
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
)

class HomeActions(
    val turnOn: () -> Unit,
    val openYouTube: () -> Unit,
    val setThemeMode: (Int) -> Unit,
    /** Unsere Superwall-Paywall. */
    val showPaywall: () -> Unit,
    val restore: () -> Unit,
    val manageSubscription: () -> Unit,
    val rateApp: () -> Unit,
    /** Code einlösen, gibt die Meldung für den Nutzer zurück. */
    val redeem: (String) -> String,
    val restartSetup: () -> Unit,
)

private enum class Screen { Home, Settings, Privacy }

/** Leiste unten: Status, Settings, Pro. Pro ist keine Seite, sondern öffnet immer unsere Superwall-Paywall. */
@Composable
fun HomeApp(state: HomeState, actions: HomeActions) {
    var screen by remember { mutableStateOf(Screen.Home) }
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
            onPro = actions.showPaywall,
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
        NavItem(Icons.Outlined.Shield, "Status", selected == 0, onHome)
        NavItem(Icons.Outlined.Tune, "Settings", selected == 1, onSettings)
        NavItem(Icons.Outlined.WorkspacePremium, "Pro", false, onPro)
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

@Composable
private fun HomeScreen(state: HomeState, actions: HomeActions) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
    ) {
        // Ohne Testzeit/Abo/Code arbeitet die App nicht: dann zählt das als "aus".
        val active = state.enabled && state.isPro
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandLogo(34.dp)
            Spacer(Modifier.width(10.dp))
            BrandName(22.sp, p.text)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            StatusOrb(active)
            Spacer(Modifier.height(26.dp))
            val accent = if (active) Brand.Green else Brand.Red
            Text("Protection", style = headline(40.sp), color = p.text)
            Text(if (active) "on" else "off", style = headline(40.sp), color = accent)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    active -> "YouTube is Shorts-free."
                    !state.isPro -> "Start your free trial to block Shorts."
                    else -> "Shorts are back in YouTube."
                },
                color = p.subtext,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(30.dp))
            // Eine Zahl, die zählt.
            GlassCard(padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${state.shortsClosed}", style = headline(38.sp), color = p.text)
                        Text("Shorts closed for you", color = p.subtext, fontSize = 15.sp)
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !state.isPro, onClick = actions.showPaywall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Schedule, null, tint = p.text, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (state.isPro) {
                            Text(timeSaved(state.shortsClosed), style = headline(20.sp), color = p.text)
                            Text("Time saved (estimate)", color = p.subtext, fontSize = 13.sp)
                        } else {
                            Text("Time saved", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("See how much time you got back", color = p.subtext, fontSize = 13.sp)
                        }
                    }
                    if (!state.isPro) ProPill()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        when {
            !state.isPro -> MainButton("Try 3 days free", actions.showPaywall)
            !state.enabled -> MainButton("Turn on protection", actions.turnOn)
            else -> MainButton("Open YouTube", actions.openYouTube, Icons.AutoMirrored.Outlined.OpenInNew)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Grosser Kreis mit Schild, leuchtet grün (an) oder rot (aus). */
@Composable
private fun StatusOrb(enabled: Boolean) {
    val accent = if (enabled) Brand.Green else Brand.Red
    Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(196.dp)) {
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent)),
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
                if (enabled) Icons.Outlined.GppGood else Icons.Outlined.GppBad,
                null,
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

/** Schätzung: Wer in einem Short landet, bleibt im Schnitt ein paar Minuten hängen. */
private fun timeSaved(shortsClosed: Int): String {
    val minutes = shortsClosed * 3 / 2
    return if (minutes >= 60) "≈ ${minutes / 60} h ${minutes % 60} min" else "≈ $minutes min"
}

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
        RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
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
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        Text("Settings", style = headline(36.sp), color = p.text, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

        SectionLabel("SUBSCRIPTION")
        if (state.isPro) {
            GlassCard(padding = 0.dp) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandLogo(44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("F*ck Shorts Pro", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (state.promo) "Unlocked with a code" else "Active",
                            color = Brand.Green,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (!state.promo) {
                    Divider()
                    LinkRow(Icons.AutoMirrored.Outlined.OpenInNew, "Manage subscription", "Switch to yearly or cancel", actions.manageSubscription)
                }
            }
        } else {
            // Wie die Paywall: roter Verlauf, weisser Knopf.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF8E1219), Color(0xFFB3161F), Brand.Red)))
                    .clickable(onClick = actions.showPaywall)
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Get Pro", style = headline(26.sp), color = Color.White)
                }
                Text(
                    "Try 3 days free. Cancel anytime in Google Play.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color.White),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Try 3 days free", color = Brand.RedText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassCard(padding = 0.dp) {
            LinkRow(Icons.Outlined.Redeem, "Redeem code", null) { redeemOpen = true }
            Divider()
            LinkRow(Icons.Outlined.Refresh, "Restore purchases", null, actions.restore)
        }

        SectionLabel("APPEARANCE")
        GlassCard(padding = 14.dp) {
            Segmented(listOf("System", "Dark", "Light"), state.themeMode, actions.setThemeMode)
        }

        SectionLabel("ABOUT")
        GlassCard(padding = 0.dp) {
            LinkRow(Icons.Outlined.StarOutline, "Rate the app", null, actions.rateApp)
            Divider()
            LinkRow(Icons.Outlined.Description, "Privacy & permissions", null, onPrivacy)
        }

        Spacer(Modifier.height(28.dp))
        Box(Modifier.align(Alignment.CenterHorizontally)) { BrandLogo(40.dp) }
        Spacer(Modifier.height(8.dp))
        Text(
            "Version ${state.version}\nNot affiliated with YouTube or Google.",
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
            Text("Run setup again", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
    }
    if (redeemOpen) RedeemDialog(actions.redeem, onClose = { redeemOpen = false })
}

@Composable
private fun RedeemDialog(redeem: (String) -> String, onClose: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF1A1A1C),
        title = { Text("Redeem code", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        message = null
                    },
                    singleLine = true,
                    placeholder = { Text("Code") },
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
                Text("Redeem", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Close", color = Color.White.copy(alpha = 0.7f)) }
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
        ScreenHeader("Privacy", onBack)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF1A1A1C))) {
            Disclosure()
        }
        Spacer(Modifier.height(24.dp))
    }
}
