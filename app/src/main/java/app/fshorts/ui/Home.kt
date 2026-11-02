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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
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
    val isPro: Boolean,
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
)

private enum class Screen { Home, Settings, Privacy }

/** Nur eine Hauptseite; Einstellungen oben rechts. Pro ist immer unsere Superwall-Paywall. */
@Composable
fun HomeApp(state: HomeState, actions: HomeActions) {
    var screen by remember { mutableStateOf(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen == Screen.Privacy) Screen.Settings else Screen.Home
    }
    val p = LocalPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(if (p.dark) Brand.strongGradient else p.background),
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Home -> HomeScreen(state, actions, onSettings = { screen = Screen.Settings })
                Screen.Settings -> SettingsScreen(
                    state,
                    actions,
                    onBack = { screen = Screen.Home },
                    onPrivacy = { screen = Screen.Privacy },
                )
                Screen.Privacy -> PrivacyScreen(onBack = { screen = Screen.Settings })
            }
        }
    }
}

@Composable
private fun HomeScreen(state: HomeState, actions: HomeActions, onSettings: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        // Kopf: Marke links, Einstellungen rechts.
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandLogo(34.dp)
            Spacer(Modifier.width(10.dp))
            BrandName(22.sp, p.text)
            Spacer(Modifier.weight(1f))
            RoundIconButton(Icons.Outlined.Settings, "Settings", onSettings)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            StatusOrb(state.enabled)
            Spacer(Modifier.height(26.dp))
            val accent = if (state.enabled) Brand.Green else Brand.Red
            Text("Protection", style = headline(40.sp), color = p.text)
            Text(if (state.enabled) "on" else "off", style = headline(40.sp), color = accent)
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.enabled) "YouTube is Shorts-free." else "Shorts are back in YouTube.",
                color = p.subtext,
                fontSize = 17.sp,
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
        if (state.enabled) {
            MainButton("Open YouTube", actions.openYouTube, Icons.AutoMirrored.Outlined.OpenInNew)
        } else {
            MainButton("Turn on protection", actions.turnOn)
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
private fun SettingsScreen(state: HomeState, actions: HomeActions, onBack: () -> Unit, onPrivacy: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
    ) {
        ScreenHeader("Settings", onBack)

        SectionLabel("SUBSCRIPTION")
        if (state.isPro) {
            GlassCard(padding = 0.dp) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandLogo(44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("F*ck Shorts Pro", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Active", color = Brand.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Divider()
                LinkRow(Icons.AutoMirrored.Outlined.OpenInNew, "Manage subscription", "Switch to yearly or cancel", actions.manageSubscription)
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
            Spacer(Modifier.height(10.dp))
            GlassCard(padding = 0.dp) {
                LinkRow(Icons.Outlined.Refresh, "Restore purchases", null, actions.restore)
            }
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
        Spacer(Modifier.height(24.dp))
    }
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
            .statusBarsPadding()
            .navigationBarsPadding()
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
