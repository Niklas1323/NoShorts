package app.fshorts.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import app.fshorts.FShortsApp
import app.fshorts.R
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Onboarding im Stil der Paywall: drei Seiten, die letzte mit dem vollständigen Hinweis zur Bedienungshilfe
 * (Pflicht für den Play Store: vor dem Einschalten, mit Zustimmen und Ablehnen).
 */
@Composable
fun Onboarding(
    startStep: Int,
    accepted: Boolean,
    /** Bedienungshilfe schon an (z. B. "Run setup again"): dann geht es mit "Continue" direkt weiter. */
    enabled: Boolean,
    onAgree: () -> Unit,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    /** Abo/Testzeit oder Code aktiv: dann ist der letzte Schritt (Testzeit) erledigt. */
    isPro: Boolean,
    onStartTrial: () -> Unit,
    onRestore: () -> Unit,
    onRedeem: (String) -> String,
    /** Onboarding fertig (nur mit Testzeit, Abo oder Code). */
    onDone: () -> Unit,
) {
    // Saveable: Sprachwechsel baut die Seite neu auf, man bleibt auf derselben Seite.
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    var paywallShown by remember { mutableStateOf(false) }
    // Paywall nur nach Tipp auf "Continue" (vorher kam sie von selbst, sobald die Bedienungshilfe an war, und die
    // Seite "Turn it on" wurde übersprungen). Der Zwischenschritt [TrialPage] ist abgeschaltet (SHOW_TRIAL_STEP).
    val continueTapped: () -> Unit = {
        if (SHOW_TRIAL_STEP && accepted && enabled && !isPro) {
            step = 3
        } else {
            paywallShown = true
            onContinue()
        }
    }
    // Testzeit gestartet (oder Code): fertig.
    LaunchedEffect(isPro) { if (isPro && paywallShown) onDone() }
    // Zurück (Wischen oder Taste) führt auf die vorige Seite.
    BackHandler(enabled = step > startStep) { step-- }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brand.strongGradient),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally { w -> if (forward) w / 4 else -w / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { w -> if (forward) -w / 4 else w / 4 } + fadeOut())
            },
            label = "onboarding",
        ) { page ->
            when (page) {
                0 -> WelcomePage(onNext = { step = 1 })
                1 -> BeforeAfterPage(onNext = { step = 2 })
                2 -> TurnOnPage(accepted, enabled, isPro, onAgree, continueTapped, onOpenSettings, onOpenAppInfo, onRedeem)
                else -> TrialPage(isPro, onStartTrial, onRestore, onRedeem, onDone)
            }
        }
    }
}

@Composable
private fun Page(
    step: Int,
    button: String,
    onButton: () -> Unit,
    buttonIcon: ImageVector? = null,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        footer()
        WhiteButton(button, onButton, icon = buttonIcon)
        Dots(step)
    }
}

@Composable
fun WhiteButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(Color.White)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = Brand.RedText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 21.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (icon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(icon, null, tint = Brand.RedText)
        }
    }
}

@Composable
private fun Dots(step: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(STEPS) { i ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .height(5.dp)
                    .width(if (i == step) 22.dp else 6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == step) 1f else 0.35f)),
            )
        }
    }
}

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    Page(
        step = 0,
        button = stringResource(R.string.onb_lets_go),
        onButton = onNext,
        buttonIcon = Icons.AutoMirrored.Filled.ArrowForward,
    ) {
        // Sprache gleich auf der ersten Seite wählen (Standard: Sprache des Handys, sonst Englisch).
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
            LanguagePill()
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(44.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(24.dp),
        ) {
            BrandLogo(124.dp)
        }
        Spacer(Modifier.height(30.dp))
        Box(Modifier.align(Alignment.CenterHorizontally)) { BrandName(46.sp) }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onb_tagline),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 18.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.weight(1.3f))
    }
}

@Composable
private fun BeforeAfterPage(onNext: () -> Unit) {
    Page(step = 1, button = stringResource(R.string.onb_next), onButton = onNext) {
        Spacer(Modifier.height(40.dp))
        Text(stringResource(R.string.onb_shorts_gone), style = headline(40.sp), color = Color.White)
        Text(stringResource(R.string.onb_long_stay), style = headline(40.sp), color = Color.White.copy(alpha = 0.55f))
        Spacer(Modifier.weight(1f))
        Label(stringResource(R.string.onb_before))
        BarPreview(before = true)
        Icon(
            Icons.Filled.ArrowDownward,
            null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp),
        )
        Label(stringResource(R.string.onb_after))
        BarPreview(before = false)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/** Vorher: echte Leiste mit durchgestrichenem Shorts-Knopf. Nachher: unsere Leiste. */
@Composable
private fun BarPreview(before: Boolean) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (before) {
            BarItem(Icons.Outlined.Home, stringResource(R.string.preview_home))
            ShortsCrossed()
            PlusCircle()
            BarItem(Icons.Outlined.Subscriptions, stringResource(R.string.preview_subs))
            BarItem(Icons.Outlined.Person, stringResource(R.string.preview_you))
        } else {
            BarItem(Icons.Outlined.Home, stringResource(R.string.preview_home))
            BarItem(Icons.Outlined.Subscriptions, stringResource(R.string.preview_subs))
            BarItem(Icons.Outlined.Person, stringResource(R.string.preview_you))
            PlusCircle()
        }
    }
}

@Composable
private fun BarItem(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ShortsCrossed() {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brand.Red.copy(alpha = 0.25f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .drawWithContent {
                drawContent()
                drawLine(
                    Brand.Red,
                    Offset(size.width * 0.1f, size.height * 0.9f),
                    Offset(size.width * 0.9f, size.height * 0.1f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Smartphone, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
        Text("Shorts", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

@Composable
private fun PlusCircle() {
    Box(
        Modifier
            .size(42.dp)
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, null, tint = Color.White)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TurnOnPage(
    accepted: Boolean,
    enabled: Boolean,
    isPro: Boolean,
    onAgree: () -> Unit,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onRedeem: (String) -> String,
) {
    var declined by remember { mutableStateOf(false) }
    var redeemOpen by remember { mutableStateOf(false) }
    // Alles erledigt bis auf die Testzeit: "Continue" öffnet die Paywall (dort gibt es auch Wiederherstellen), darunter Code.
    val needsTrial = accepted && enabled && !isPro
    var lockedSheet by remember { mutableStateOf(false) }
    Page(
        step = 2,
        button = when {
            !accepted -> stringResource(R.string.turnon_agree)
            enabled -> stringResource(R.string.turnon_continue)
            else -> stringResource(R.string.turnon_open_settings)
        },
        onButton = when {
            !accepted -> onAgree
            enabled -> onContinue
            else -> onOpenSettings
        },
        footer = {
            if (needsTrial) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Text(
                        stringResource(R.string.have_code),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { redeemOpen = true }.padding(6.dp),
                    )
                }
            }
            if (!accepted) {
                Text(
                    if (declined) {
                        stringResource(R.string.turnon_declined)
                    } else {
                        stringResource(R.string.not_now)
                    },
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { declined = true }
                        .padding(bottom = 12.dp),
                )
            }
        },
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(36.dp))
            Text(stringResource(R.string.turnon_title), style = headline(40.sp), color = Color.White)
            Text(stringResource(R.string.turnon_sub), color = Color.White.copy(alpha = 0.7f), fontSize = 17.sp)
            Spacer(Modifier.height(20.dp))
            StepRow(1, boldArg(stringResource(R.string.step_open), stringResource(R.string.accessibility)))
            StepRow(2, boldArg(stringResource(R.string.step_tap), "F*ck Shorts"), trailing = { BrandLogo(30.dp) })
            StepRow(3, AnnotatedString(stringResource(R.string.step_switch)), trailing = { FakeSwitch() })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { lockedSheet = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Lock, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.switch_greyed), color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.height(10.dp))
            Disclosure(collapsible = true)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (redeemOpen) RedeemDialog(onRedeem, onClose = { redeemOpen = false })
    if (lockedSheet) {
        ModalBottomSheet(
            onDismissRequest = { lockedSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF17171A),
        ) {
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 24.dp)) {
                Text(stringResource(R.string.locked_title), style = headline(28.sp), color = Color.White)
                Text(
                    stringResource(R.string.locked_sub),
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                )
                StepRow(1, boldArg(stringResource(R.string.step_open), stringResource(R.string.app_info)), dark = true)
                StepRow(2, AnnotatedString(stringResource(R.string.locked_step2)), dark = true, trailing = {
                    Icon(Icons.Filled.MoreVert, null, tint = Color.White)
                })
                StepRow(3, boldArg("%1\$s", stringResource(R.string.allow_restricted)), dark = true)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brand.Red)
                        .clickable {
                            lockedSheet = false
                            onOpenAppInfo()
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.step_open, stringResource(R.string.app_info)),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    stringResource(R.string.close),
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { lockedSheet = false }
                        .padding(14.dp),
                )
            }
        }
    }
}

@Composable
private fun StepRow(
    number: Int,
    text: AnnotatedString,
    dark: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .padding(vertical = 5.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = Color.Black, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun FakeSwitch() {
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(CircleShape)
            .background(Brand.Green)
            .padding(3.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White))
    }
}

/**
 * Vollständiger Hinweis (Play-Richtlinie "Use of the AccessibilityService API"). Muss vor dem Einschalten zu sehen
 * sein und genau sagen, was gelesen wird, was die App tut und wohin Daten gehen. Im Onboarding zuerst zugeklappt
 * (Zusammenfassung + "Show details"), die Knöpfe stehen direkt darunter. Bei Änderungen: Formular in der Play
 * Console neu abgeben und `Prefs.DISCLOSURE` hochzählen.
 */
@Composable
fun Disclosure(collapsible: Boolean = false, textColor: Color = Color.White) {
    var expanded by remember { mutableStateOf(!collapsible) }
    val sub = textColor.copy(alpha = 0.8f)
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.disc_title), color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            stringResource(R.string.disc_summary),
            color = sub, fontSize = 14.sp,
        )
        if (collapsible) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(if (expanded) R.string.hide_details else R.string.show_details),
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    null,
                    tint = textColor,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.disc_reads), color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Bullet(stringResource(R.string.disc_r1), sub)
                Bullet(stringResource(R.string.disc_r2), sub)
                Bullet(stringResource(R.string.disc_r3), sub)
                Bullet(stringResource(R.string.disc_r4), sub)
                Text(stringResource(R.string.disc_does), color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Bullet(stringResource(R.string.disc_d1), sub)
                Bullet(stringResource(R.string.disc_d2), sub)
                Bullet(stringResource(R.string.disc_d3), sub)
                Text(stringResource(R.string.disc_data), color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Bullet(stringResource(R.string.disc_p1), sub)
                Bullet(
                    stringResource(R.string.disc_p2) +
                        // Test-Versionen schreiben zusätzlich Fehler-Dateien, die Play-Store-Version nicht.
                        if (app.fshorts.BuildConfig.DEBUG) " " + stringResource(R.string.disc_p2_debug) else "",
                    sub,
                )
                Bullet(stringResource(R.string.disc_p3), sub)
                Bullet(stringResource(R.string.disc_p4), sub)
                Bullet(stringResource(R.string.disc_p5), sub)
                val uriHandler = LocalUriHandler.current
                Text(
                    stringResource(R.string.disc_policy),
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { runCatching { uriHandler.openUri(FShortsApp.PRIVACY_POLICY_URL) } }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Bullet(text: String, color: Color) {
    Row {
        Text("•  ", color = color, fontSize = 14.sp)
        Text(text, color = color, fontSize = 14.sp)
    }
}

private const val SHOW_TRIAL_STEP = false
private val STEPS = if (SHOW_TRIAL_STEP) 4 else 3

/**
 * Letzter Schritt: 3 Tage gratis starten (Superwall-Paywall). Ohne Testzeit, Abo oder Code geht es nicht weiter.
 * Sobald eins davon aktiv ist, ist das Onboarding fertig.
 */
@Composable
private fun TrialPage(
    isPro: Boolean,
    onStartTrial: () -> Unit,
    onRestore: () -> Unit,
    onRedeem: (String) -> String,
    onDone: () -> Unit,
) {
    var redeemOpen by remember { mutableStateOf(false) }
    LaunchedEffect(isPro) { if (isPro) onDone() }
    Page(
        step = 3,
        button = stringResource(R.string.start_trial),
        onButton = onStartTrial,
        footer = {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text(
                    stringResource(R.string.restore_purchases),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onRestore).padding(6.dp),
                )
                Text(
                    stringResource(R.string.have_code),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { redeemOpen = true }.padding(6.dp),
                )
            }
        },
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.align(Alignment.CenterHorizontally)) { BrandLogo(96.dp) }
        Spacer(Modifier.height(26.dp))
        Text(stringResource(R.string.trial_last_step), style = headline(36.sp), color = Color.White.copy(alpha = 0.6f))
        Text(stringResource(R.string.trial_start_free), style = headline(36.sp), color = Color.White)
        Spacer(Modifier.height(20.dp))
        TrialPoint(stringResource(R.string.trial_today), stringResource(R.string.trial_today_text))
        TrialPoint(stringResource(R.string.trial_day2), stringResource(R.string.trial_day2_text))
        TrialPoint(stringResource(R.string.trial_day3), stringResource(R.string.trial_day3_text))
        Spacer(Modifier.weight(1.2f))
    }
    if (redeemOpen) RedeemDialog(onRedeem, onClose = { redeemOpen = false })
}

@Composable
private fun TrialPoint(title: String, text: String) {
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 2.dp).size(26.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, null, tint = Brand.RedText, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(text, color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp)
        }
    }
}

/**
 * Abo zu Ende (gekündigt oder nicht verlängert): erst erklären, dann die Paywall. Ohne Abo tut die App nichts.
 */
@Composable
fun ExpiredScreen(onResubscribe: () -> Unit, onRestore: () -> Unit, onRedeem: (String) -> String) {
    var redeemOpen by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brand.strongGradient),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.align(Alignment.CenterHorizontally)) { BrandLogo(96.dp) }
                Spacer(Modifier.height(26.dp))
                Text(stringResource(R.string.expired_1), style = headline(36.sp), color = Color.White.copy(alpha = 0.6f))
                Text(stringResource(R.string.expired_2), style = headline(36.sp), color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.expired_text),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 17.sp,
                )
                Spacer(Modifier.weight(1.2f))
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text(
                    stringResource(R.string.restore_purchases),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onRestore).padding(6.dp),
                )
                Text(
                    stringResource(R.string.have_code),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { redeemOpen = true }.padding(6.dp),
                )
            }
            WhiteButton(stringResource(R.string.resubscribe), onResubscribe)
            Spacer(Modifier.height(20.dp))
        }
    }
    if (redeemOpen) RedeemDialog(onRedeem, onClose = { redeemOpen = false })
}

/** "Öffne %1$s" → Platzhalter fett. So stimmt die Wortstellung in jeder Sprache. */
private fun boldArg(template: String, arg: String): AnnotatedString = buildAnnotatedString {
    val i = template.indexOf("%1\$s")
    if (i < 0) {
        append(template)
        return@buildAnnotatedString
    }
    append(template.substring(0, i))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(arg) }
    append(template.substring(i + 4))
}
