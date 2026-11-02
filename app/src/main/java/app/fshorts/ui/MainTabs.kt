package app.fshorts.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Was die Hauptseiten anzeigen und auslösen können. */
class MainState(
    val enabled: Boolean,
    val shortsClosed: Int,
    val shelvesHidden: Int,
    val isPro: Boolean,
    val autoHide: Boolean,
    val themeMode: Int,
    val debug: Boolean,
    val dumpCount: Int,
    val version: String,
)

class MainActions(
    val turnOn: () -> Unit,
    val openYouTube: () -> Unit,
    val setAutoHide: (Boolean) -> Unit,
    val setThemeMode: (Int) -> Unit,
    val setDebug: (Boolean) -> Unit,
    val shareDumps: () -> Unit,
    val clearDumps: () -> Unit,
    val showPaywall: () -> Unit,
    val restore: () -> Unit,
    val manageSubscription: () -> Unit,
    val rateApp: () -> Unit,
)

private enum class Tab { Status, Settings, Pro }

@Composable
fun MainTabs(state: MainState, actions: MainActions) {
    val p = LocalPalette.current
    var tab by remember { mutableIntStateOf(0) }
    var subPage by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = subPage != null) { subPage = null }
    BackHandler(enabled = subPage == null && tab != 0) { tab = 0 }
    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                subPage == "about" -> AboutPage(state, actions, onBack = { subPage = null })
                subPage == "privacy" -> PrivacyPage(onBack = { subPage = "about" })
                Tab.entries[tab] == Tab.Status -> StatusPage(state, actions)
                Tab.entries[tab] == Tab.Settings -> SettingsPage(state, actions, onAbout = { subPage = "about" })
                else -> ProPage(state, actions)
            }
        }
        BottomNav(tab) {
            tab = it
            subPage = null
        }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (p.dark) Color.Black.copy(alpha = 0.35f) else Color.White)
            .padding(top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItem(Icons.Outlined.Shield, "Status", selected == 0) { onSelect(0) }
        NavItem(Icons.Outlined.Tune, "Settings", selected == 1) { onSelect(1) }
        NavItem(Icons.Outlined.WorkspacePremium, "Pro", selected == 2) { onSelect(2) }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) p.text.copy(alpha = 0.12f) else Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 5.dp),
        ) {
            Icon(icon, null, tint = if (selected) p.text else p.subtext, modifier = Modifier.size(22.dp))
        }
        Text(
            label,
            color = if (selected) p.text else p.subtext,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusPage(state: MainState, actions: MainActions) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            GlassCard(
                border = if (state.enabled) null else Brand.Red.copy(alpha = 0.45f),
                padding = 26.dp,
            ) {
                val accent = if (state.enabled) Brand.Green else Brand.Red
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(84.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.enabled) Icons.Outlined.GppGood else Icons.Outlined.GppBad,
                            null,
                            tint = accent,
                            modifier = Modifier.size(42.dp),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Protection ", style = headline(34.sp), color = p.text)
                    Text(if (state.enabled) "on" else "off", style = headline(34.sp), color = accent)
                }
                Text(
                    if (state.enabled) "YouTube is Shorts-free." else "Shorts are back in YouTube.",
                    color = p.subtext,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            val dim = if (state.enabled) 1f else 0.5f
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), "${state.shortsClosed}", "Shorts closed", dim)
                StatCard(Modifier.weight(1f), "${state.shelvesHidden}", "Shelves hidden", dim)
            }
            if (state.enabled) {
                GlassCard(padding = 16.dp, modifier = Modifier.clickable(enabled = !state.isPro) { actions.showPaywall() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(p.text.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Schedule, null, tint = p.text)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (state.isPro) timeSaved(state.shortsClosed) else "≈ ? h ?? min",
                                style = headline(22.sp),
                                color = p.text,
                            )
                            Text(
                                if (state.isPro) "Time saved (estimate)" else "Time saved · Pro",
                                color = p.subtext,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (state.enabled) {
            SecondaryButton("Open YouTube", Icons.AutoMirrored.Outlined.OpenInNew, actions.openYouTube)
        } else {
            RedButton("Turn on protection", actions.turnOn)
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** Schätzung: Wer in einem Short landet, bleibt im Schnitt ein paar Minuten hängen. */
private fun timeSaved(shortsClosed: Int): String {
    val minutes = shortsClosed * 3 / 2
    return if (minutes >= 60) "≈ ${minutes / 60} h ${minutes % 60} min" else "≈ $minutes min"
}

@Composable
private fun StatCard(modifier: Modifier, value: String, label: String, alpha: Float) {
    val p = LocalPalette.current
    GlassCard(modifier, padding = 16.dp) {
        Text(value, style = headline(30.sp), color = p.text.copy(alpha = alpha))
        Text(label, color = p.subtext.copy(alpha = p.subtext.alpha * alpha), fontSize = 14.sp)
    }
}

@Composable
private fun RedButton(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(Brand.Red)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryButton(text: String, icon: ImageVector?, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(29.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.cardBorder, shape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (icon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(icon, null, tint = p.text)
        }
    }
}

@Composable
private fun SettingsPage(state: MainState, actions: MainActions, onAbout: () -> Unit) {
    val p = LocalPalette.current
    PageColumn {
        Text("Settings", style = headline(38.sp), color = p.text, modifier = Modifier.padding(vertical = 8.dp))
        GlassCard(padding = 16.dp) {
            SettingRow(Icons.Outlined.VisibilityOff, "Auto-hide Shorts shelves") {
                FsSwitch(state.autoHide, actions.setAutoHide)
            }
        }
        GlassCard(padding = 16.dp) {
            SettingRow(Icons.Outlined.Contrast, "Theme") {}
            Spacer(Modifier.height(10.dp))
            Segmented(listOf("System", "Dark", "Light"), state.themeMode, actions.setThemeMode)
        }
        Text(
            "DEBUGGING",
            color = p.subtext,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        GlassCard(padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BugReport, null, tint = p.text, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Save every screen change", color = p.text, fontSize = 16.sp)
                    Text("${state.dumpCount} files saved", color = p.subtext, fontSize = 13.sp)
                }
                FsSwitch(state.debug, actions.setDebug)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallButton(Modifier.weight(1f), "Share", Icons.Outlined.Share, p.text, state.dumpCount > 0, actions.shareDumps)
                SmallButton(Modifier.weight(1f), "Delete", Icons.Outlined.Delete, Brand.Red, state.dumpCount > 0, actions.clearDumps)
            }
            Text(
                "Long-press our bar in YouTube to save the current screen.",
                color = p.subtext,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        GlassCard(padding = 16.dp, modifier = Modifier.clickable(onClick = onAbout)) {
            SettingRow(Icons.Outlined.Info, "About") {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.subtext)
            }
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, trailing: @Composable () -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = p.text, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun FsSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Brand.Green,
            checkedThumbColor = Color.White,
            checkedBorderColor = Brand.Green,
        ),
    )
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
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (i == selected) p.text.copy(alpha = if (p.dark) 0.14f else 1f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = when {
                        i == selected && !p.dark -> Color.White
                        i == selected -> p.text
                        else -> p.subtext
                    },
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SmallButton(
    modifier: Modifier,
    text: String,
    icon: ImageVector,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(p.text.copy(alpha = 0.06f))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (enabled) color else p.faint
        Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = c, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProPage(state: MainState, actions: MainActions) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(36.dp))
            Box(Modifier.align(Alignment.CenterHorizontally)) {
                BrandLogo(96.dp)
                Text(
                    "PRO",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 0.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (state.isPro) "You're Pro" else "F*ck Shorts Pro",
                style = headline(32.sp),
                color = p.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(22.dp))
            ProFeature("Shorts blocked in YouTube")
            ProFeature("Shorts shelves hidden automatically")
            ProFeature("Time-saved stats")
            ProFeature("Every future Pro feature")
        }
        if (state.isPro) {
            SecondaryButton("Manage subscription", Icons.AutoMirrored.Outlined.OpenInNew, actions.manageSubscription)
            Text(
                "Switch to yearly in Google Play subscriptions.",
                color = p.subtext,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        } else {
            RedButton("Try 3 days free", actions.showPaywall)
            Text(
                "Restore purchases",
                color = p.subtext,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = actions.restore)
                    .padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun ProFeature(text: String) {
    val p = LocalPalette.current
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Brand.Green.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, null, tint = Brand.Green, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = p.text, fontSize = 17.sp)
    }
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            "Back",
            tint = p.text,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(6.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(title, style = headline(32.sp), color = p.text)
    }
}

@Composable
private fun AboutPage(state: MainState, actions: MainActions, onBack: () -> Unit) {
    val p = LocalPalette.current
    var privacy by remember { mutableStateOf(false) }
    if (privacy) {
        PrivacyPage(onBack = { privacy = false })
        BackHandler { privacy = false }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        BackHeader("About", onBack)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.align(Alignment.CenterHorizontally)) { BrandLogo(84.dp) }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.align(Alignment.CenterHorizontally)) { BrandName(28.sp, p.text) }
        Text(
            "Version ${state.version}",
            color = p.subtext,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(24.dp))
        GlassCard(padding = 0.dp) {
            LinkRow(Icons.Outlined.StarOutline, "Rate the app", actions.rateApp)
            LinkRow(Icons.Outlined.Description, "Privacy & permissions") { privacy = true }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Not affiliated with YouTube or Google.",
            color = p.subtext,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        )
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = p.text, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(title, color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.subtext)
    }
}

/** Derselbe Hinweis wie beim Einschalten, jederzeit nachzulesen. */
@Composable
private fun PrivacyPage(onBack: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        BackHeader("Privacy", onBack)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (p.dark) Color.Transparent else Color(0xFF1A1A1C)),
        ) {
            Disclosure()
        }
        Spacer(Modifier.height(16.dp))
    }
}
