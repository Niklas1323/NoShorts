package app.fshorts.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fshorts.R

// -------------------------------------------------------------------------------------------------------------------
// Onboarding und "Abo beendet" im Design V2 (wie die Startseite V2): flaches YouTube-Schwarz, graue Flächen, roter
// Hauptknopf, gleiche Abläufe und Texte wie V1 (Onboarding.kt). Immer dunkel (wie V1).
// -------------------------------------------------------------------------------------------------------------------

/** Wie [Onboarding] (gleiche Schritte und Regeln), nur im Design V2. */
@Composable
fun OnboardingV2(
    startStep: Int,
    accepted: Boolean,
    enabled: Boolean,
    onAgree: () -> Unit,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    isPro: Boolean,
    onRedeem: (String) -> String,
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    var paywallShown by remember { mutableStateOf(false) }
    // Paywall nur nach Tipp auf "Weiter" (wie V1 mit SHOW_TRIAL_STEP = false).
    val continueTapped: () -> Unit = {
        paywallShown = true
        onContinue()
    }
    LaunchedEffect(isPro) { if (isPro && paywallShown) onDone() }
    BackHandler(enabled = step > startStep) { step-- }
    CompositionLocalProvider(LocalV2ForceDark provides true) {
        Box(Modifier.fillMaxSize().background(V2Dark.bg)) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally { w -> if (forward) w / 5 else -w / 5 } + fadeIn()) togetherWith
                        (slideOutHorizontally { w -> if (forward) -w / 5 else w / 5 } + fadeOut())
                },
                label = "onboarding",
            ) { page ->
                when (page) {
                    0 -> WelcomeV2(onNext = { step = 1 })
                    1 -> BeforeAfterV2(onNext = { step = 2 })
                    else -> TurnOnV2(accepted, enabled, isPro, onAgree, continueTapped, onOpenSettings, onOpenAppInfo, onRedeem)
                }
            }
        }
    }
}

private const val STEPS_V2 = 3
/** Zeilen im Blatt (Blatt ist #212121): etwas heller, damit man sie sieht. */
private val SHEET_ROW = Color(0xFF2E2E2E)

@Composable
private fun PageV2(
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
            .padding(horizontal = 20.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        footer()
        PrimaryButtonV2(button, onButton, buttonIcon)
        DotsV2(step)
    }
}

@Composable
private fun DotsV2(step: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), horizontalArrangement = Arrangement.Center) {
        repeat(STEPS_V2) { i ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .height(4.dp)
                    .width(if (i == step) 20.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (i == step) V2Dark.text else V2Dark.text.copy(alpha = 0.25f)),
            )
        }
    }
}

@Composable
private fun WelcomeV2(onNext: () -> Unit) {
    val c = v2()
    PageV2(
        step = 0,
        button = stringResource(R.string.onb_lets_go),
        onButton = onNext,
        buttonIcon = Icons.AutoMirrored.Filled.ArrowForward,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
            LanguagePill()
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.align(Alignment.CenterHorizontally)) { BrandLogo(112.dp, background = V2.Red) }
        Spacer(Modifier.height(28.dp))
        Text(
            buildAnnotatedString {
                append("F")
                withStyle(SpanStyle(color = V2.Red)) { append("*") }
                append("ck Shorts")
            },
            color = c.text,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onb_tagline),
            color = c.sub,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.weight(1.3f))
    }
}

@Composable
private fun BeforeAfterV2(onNext: () -> Unit) {
    val c = v2()
    PageV2(step = 1, button = stringResource(R.string.onb_next), onButton = onNext) {
        Spacer(Modifier.height(48.dp))
        Text(stringResource(R.string.onb_shorts_gone), color = c.text, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.onb_long_stay), color = c.sub, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(sentenceCase(stringResource(R.string.onb_before)), color = c.sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
        BeforeBarV2()
        Spacer(Modifier.height(22.dp))
        Text(sentenceCase(stringResource(R.string.onb_after)), color = c.sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
        // Nachher: genau unsere Leiste, wie sie in YouTube aussieht.
        ScaledBarPreview(modern = false, dark = true, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.weight(1f))
    }
}

/** Vorher: YouTubes Leiste mit Shorts-Knopf (durchgestrichen), im selben Massstab wie die Vorschau darunter. */
@Composable
private fun BeforeBarV2() {
    val c = v2()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C1C))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BeforeItem(Icons.Outlined.Home, stringResource(R.string.preview_home))
        Column(
            Modifier.drawWithContent {
                drawContent()
                drawLine(
                    V2.Red,
                    Offset(size.width * 0.05f, size.height * 0.95f),
                    Offset(size.width * 0.95f, size.height * 0.05f),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Slideshow, null, tint = c.sub, modifier = Modifier.size(22.dp))
            Text("Shorts", color = c.sub, fontSize = 10.sp, maxLines = 1)
        }
        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF2B2B2B)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, null, tint = c.text, modifier = Modifier.size(20.dp))
        }
        BeforeItem(Icons.Outlined.Subscriptions, stringResource(R.string.preview_subs))
        BeforeItem(Icons.Outlined.AccountCircle, stringResource(R.string.preview_you))
    }
}

@Composable
private fun BeforeItem(icon: ImageVector, label: String) {
    val c = v2()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = c.text, modifier = Modifier.size(22.dp))
        Text(label, color = c.text, fontSize = 10.sp, maxLines = 1, softWrap = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TurnOnV2(
    accepted: Boolean,
    enabled: Boolean,
    isPro: Boolean,
    onAgree: () -> Unit,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onRedeem: (String) -> String,
) {
    val c = v2()
    var declined by remember { mutableStateOf(false) }
    var redeemOpen by remember { mutableStateOf(false) }
    var lockedSheet by remember { mutableStateOf(false) }
    val needsTrial = accepted && enabled && !isPro
    PageV2(
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
                TextLinkV2(stringResource(R.string.have_code)) { redeemOpen = true }
            }
            if (!accepted) {
                TextLinkV2(if (declined) stringResource(R.string.turnon_declined) else stringResource(R.string.not_now)) { declined = true }
            }
        },
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(40.dp))
            Text(stringResource(R.string.turnon_title), color = c.text, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.turnon_sub), color = c.sub, fontSize = 15.sp)
            Spacer(Modifier.height(20.dp))
            StepRowV2(1, boldArg(stringResource(R.string.step_open), stringResource(R.string.accessibility)))
            StepRowV2(2, boldArg(stringResource(R.string.step_tap), "F*ck Shorts")) { BrandLogo(28.dp, background = V2.Red) }
            StepRowV2(3, AnnotatedString(stringResource(R.string.step_switch))) { SwitchV2() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { lockedSheet = true }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Lock, null, tint = c.sub, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.switch_greyed), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.sub)
                }
            }
            Spacer(Modifier.height(8.dp))
            Disclosure(collapsible = true, textColor = c.text, background = c.surface, border = Color.Transparent)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (redeemOpen) RedeemSheetV2(onRedeem, onClose = { redeemOpen = false })
    if (lockedSheet) {
        ModalBottomSheet(
            onDismissRequest = { lockedSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF212121),
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(stringResource(R.string.locked_title), color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.locked_sub), color = c.sub, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
                StepRowV2(1, boldArg(stringResource(R.string.step_open), stringResource(R.string.app_info)), SHEET_ROW)
                StepRowV2(2, AnnotatedString(stringResource(R.string.locked_step2)), SHEET_ROW) { Icon(Icons.Filled.MoreVert, null, tint = c.text) }
                StepRowV2(3, boldArg("%1\$s", stringResource(R.string.allow_restricted)), SHEET_ROW)
                Spacer(Modifier.height(14.dp))
                PrimaryButtonV2(stringResource(R.string.step_open, stringResource(R.string.app_info)), {
                    lockedSheet = false
                    onOpenAppInfo()
                })
                SecondaryButtonV2(stringResource(R.string.close)) { lockedSheet = false }
            }
        }
    }
}

@Composable
private fun TextLinkV2(text: String, onClick: () -> Unit) {
    val c = v2()
    Text(
        text,
        color = c.sub,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(10.dp),
    )
}

@Composable
private fun StepRowV2(
    number: Int,
    text: AnnotatedString,
    background: Color? = null,
    trailing: @Composable () -> Unit = {},
) {
    val c = v2()
    Row(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background ?: c.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(c.text), contentAlignment = Alignment.Center) {
            Text("$number", color = c.bg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, color = c.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun SwitchV2() {
    Box(
        Modifier.size(width = 44.dp, height = 26.dp).clip(CircleShape).background(V2.Green).padding(3.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White))
    }
}

/** "Abo beendet" im Design V2 (gleicher Inhalt wie [ExpiredScreen]). */
@Composable
fun ExpiredScreenV2(onResubscribe: () -> Unit, onRestore: () -> Unit, onRedeem: (String) -> String) {
    var redeemOpen by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalV2ForceDark provides true) {
        val c = v2()
        Column(
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                BrandLogo(80.dp, background = V2.Red)
                Spacer(Modifier.height(26.dp))
                Text(stringResource(R.string.expired_1), color = c.sub, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.expired_2), color = c.text, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.expired_text), color = c.sub, fontSize = 15.sp)
                Spacer(Modifier.weight(1.2f))
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Text(
                    stringResource(R.string.restore_purchases),
                    color = c.sub,
                    fontSize = 14.sp,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onRestore).padding(10.dp),
                )
                Text(
                    stringResource(R.string.have_code),
                    color = c.sub,
                    fontSize = 14.sp,
                    modifier = Modifier.clip(CircleShape).clickable { redeemOpen = true }.padding(10.dp),
                )
            }
            PrimaryButtonV2(stringResource(R.string.resubscribe), onResubscribe)
            Spacer(Modifier.height(20.dp))
        }
        if (redeemOpen) RedeemSheetV2(onRedeem, onClose = { redeemOpen = false })
    }
}
