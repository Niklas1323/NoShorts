package app.fshorts

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.result.contract.ActivityResultContracts
import app.fshorts.ui.UpdateDialog
import com.google.android.play.core.appupdate.AppUpdateInfo
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.fshorts.service.FuckShortsService
import app.fshorts.ui.FShortsTheme
import app.fshorts.ui.HomeActions
import app.fshorts.ui.HomeApp
import app.fshorts.ui.HomeState
import app.fshorts.ui.ExpiredScreen
import app.fshorts.ui.Onboarding
import kotlinx.coroutines.delay
import com.superwall.sdk.Superwall
import com.superwall.sdk.models.entitlements.SubscriptionStatus
import com.superwall.sdk.paywall.presentation.PaywallPresentationHandler
import com.superwall.sdk.paywall.presentation.internal.state.PaywallSkippedReason
import com.superwall.sdk.paywall.presentation.register

class MainActivity : ComponentActivity() {
    /** Googles Update-Bildschirm (In-App-Updates). Muss vor onStart registriert sein. */
    val updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    override fun onResume() {
        super.onResume()
        AppUpdates.resumeIfRunning(this, updateLauncher)
    }

    // Vor Android 13: gewählte App-Sprache (ab 13 macht das Android selbst).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Paywall in der App-Sprache (sobald die Paywall in Superwall übersetzt ist; sonst Englisch).
        runCatching { Superwall.instance.localeIdentifier = AppLanguage.current(this).code }
        setContent { App() }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    var themeMode by remember { mutableIntStateOf(Prefs.themeMode(context)) }
    var enabled by remember { mutableStateOf(isServiceEnabled(context)) }
    var accepted by remember { mutableStateOf(Prefs.disclosureAccepted(context)) }
    var onboardingDone by remember { mutableStateOf(Prefs.onboardingDone(context)) }
    // Saveable: Ein Sprachwechsel baut die Activity neu auf, der Stand der Einrichtung bleibt.
    var showSetup by rememberSaveable { mutableStateOf(false) }
    // "Run setup again": Einrichtung nochmals zeigen, aber ohne Paywall (war schon einmal fertig).
    var rerunSetup by rememberSaveable { mutableStateOf(false) }
    /** In die Android-Einstellungen geschickt: beim Zurückkommen und eingeschaltet geht es weiter. */
    var awaitingEnable by rememberSaveable { mutableStateOf(false) }
    var onboardingKey by rememberSaveable { mutableIntStateOf(0) }
    var shortsClosed by remember { mutableIntStateOf(Prefs.shortsBlocked(context)) }
    var promo by remember { mutableStateOf(Access.promo) }
    val subscription by Superwall.instance.subscriptionStatus.collectAsState()
    // Testzeit und Abo melden sich bei Superwall als "Active". Unbekannt (noch keine Antwort): letzter Stand.
    val subscribed = when (subscription) {
        is SubscriptionStatus.Active -> true
        is SubscriptionStatus.Inactive -> false
        else -> Access.subscribed
    }
    val isPro = subscribed || promo
    // Schutz pausieren: erst nach einer Wartezeit (läuft über die Uhr, also auch bei geschlossener App weiter).
    var paused by remember { mutableStateOf(Protection.paused) }
    var unlockAt by remember { mutableLongStateOf(Prefs.unlockAt(context)) }
    var nextWait by remember { mutableIntStateOf(Prefs.nextWaitMinutes(context)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var fastTimer by remember { mutableStateOf(Prefs.fastTimer(context)) }
    // Update-Hinweis: echtes Update aus Google Play oder Vorschau per Test-Code "Ningeupdate".
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var updateDemo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { AppUpdates.check(context) { updateInfo = it } }
    LaunchedEffect(unlockAt, paused) {
        while (unlockAt > 0L && !paused) {
            now = System.currentTimeMillis()
            if (now >= unlockAt) break
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }

    /** Einrichtung fertig. Nur mit Testzeit, Abo oder Code (sonst kommt man nicht aus dem Onboarding). */
    fun finishSetup() {
        awaitingEnable = false
        showSetup = false
        rerunSetup = false
        if (!onboardingDone) {
            Prefs.setOnboardingDone(context)
            onboardingDone = true
        }
    }

    /** Unsere Superwall-Paywall. Kommt sie nicht, sagt eine Meldung warum (z. B. Placement nicht im Dashboard). */
    fun showPaywall() {
        val handler = PaywallPresentationHandler()
        handler.onSkip { reason ->
            val text = when (reason) {
                is PaywallSkippedReason.PlacementNotFound ->
                    context.getString(R.string.paywall_no_placement, FShortsApp.PLACEMENT_START)
                is PaywallSkippedReason.NoAudienceMatch -> context.getString(R.string.paywall_no_audience)
                is PaywallSkippedReason.Holdout -> context.getString(R.string.paywall_holdout)
                is PaywallSkippedReason.UserIsSubscribed -> context.getString(R.string.already_pro)
                else -> context.getString(R.string.paywall_skipped)
            }
            ContextCompat.getMainExecutor(context).execute { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
        }
        handler.onError { error ->
            ContextCompat.getMainExecutor(context).execute {
                Toast.makeText(context, context.getString(R.string.paywall_error, error.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
        Superwall.instance.register(FShortsApp.PLACEMENT_START, handler = handler)
    }

    val restore: () -> Unit = {
        Superwall.instance.restorePurchases { result ->
            // Erfolg heisst nur "Abfrage hat geklappt": ob wirklich ein Abo da ist, sagt erst der Abo-Stand.
            // Kurz warten, bis Superwall ihn nach der Abfrage nachgeführt hat.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val active = Superwall.instance.subscriptionStatus.value is SubscriptionStatus.Active
                val text = context.getString(
                    when {
                        result.isFailure -> R.string.restore_failed
                        active -> R.string.restore_done
                        else -> R.string.restore_none
                    },
                )
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }, 1_000)
        }
        Unit
    }

    val redeem = { code: String ->
        when (Access.redeem(context, code)) {
            Access.Redeem.Unlocked -> {
                promo = true
                context.getString(R.string.code_unlocked)
            }
            Access.Redeem.FastTimer -> {
                fastTimer = true
                context.getString(R.string.code_fast_timer)
            }
            Access.Redeem.UpdateDemo -> {
                scope.launch {
                    delay(5_000)
                    updateDemo = true
                }
                context.getString(R.string.code_update_demo)
            }
            Access.Redeem.ShareLog -> {
                DebugLog.share(context)
                context.getString(R.string.code_log_share)
            }
            Access.Redeem.Invalid -> context.getString(R.string.code_invalid)
        }
    }

    LifecycleResumeEffect(Unit) {
        enabled = isServiceEnabled(context)
        shortsClosed = Prefs.shortsBlocked(context)
        if (enabled && accepted && awaitingEnable) {
            awaitingEnable = false
            // Weiter geht es im Onboarding mit der Testzeit; nur wer schon freigeschaltet ist, ist hier fertig.
            if (Access.unlocked) finishSetup()
        }
        onPauseOrDispose { }
    }

    // Aus "Turn on protection" kommend: Zurück führt wieder auf die Startseite.
    BackHandler(enabled = showSetup && onboardingDone && accepted) { showSetup = false }

    // Welche Seite: Einrichtung, Abo beendet oder Startseite. Wechsel immer mit sanfter Überblendung.
    val screen = when {
        !onboardingDone || showSetup || rerunSetup || !accepted -> 0
        !isPro -> 1
        else -> 2
    }
    // Symbole der Statusleiste/Navigation: helle Symbole nur auf dunklem Grund (Einrichtung und "Abo beendet" sind
    // immer dunkel, die Startseite folgt dem gewählten Design, nicht dem des Handys).
    val systemDark = isSystemInDarkTheme()
    val darkBackground = screen != 2 || themeMode == 1 || (themeMode == 0 && systemDark)
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkBackground
        controller.isAppearanceLightNavigationBars = !darkBackground
    }
    FShortsTheme(themeMode) {
      AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.96f)) togetherWith fadeOut(tween(250))
        },
        label = "app",
      ) { target ->
        if (target == 0) {
            key(onboardingKey) {
                Onboarding(
                    startStep = if (showSetup) 2 else 0,
                    accepted = accepted,
                    enabled = enabled,
                    onAgree = {
                        Prefs.setDisclosureAccepted(context)
                        accepted = true
                        if (enabled) {
                            if (Access.unlocked) finishSetup()
                        } else {
                            awaitingEnable = true
                            openAccessibilitySettings(context)
                        }
                    },
                    onContinue = { if (rerunSetup || Access.unlocked) finishSetup() else showPaywall() },
                    onOpenSettings = {
                        awaitingEnable = true
                        openAccessibilitySettings(context)
                    },
                    onOpenAppInfo = { openAppInfo(context) },
                    isPro = isPro,
                    onStartTrial = { showPaywall() },
                    onRestore = restore,
                    onRedeem = redeem,
                    onDone = { finishSetup() },
                )
            }
        } else if (target == 1) {
            ExpiredScreen(onResubscribe = { showPaywall() }, onRestore = restore, onRedeem = redeem)
        } else {
            HomeApp(
                HomeState(
                    enabled = enabled,
                    shortsClosed = shortsClosed,
                    isPro = isPro,
                    promo = promo,
                    themeMode = themeMode,
                    version = BuildConfig.VERSION_NAME,
                    paused = paused,
                    unlockAt = unlockAt,
                    now = now,
                    nextWaitMinutes = nextWait,
                    fastTimer = fastTimer,
                    startedAt = Prefs.startedAt(context),
                    supportId = FShortsApp.supportId(),
                ),
                HomeActions(
                    turnOn = { showSetup = true },
                    openYouTube = { openYouTube(context) },
                    setThemeMode = {
                        themeMode = it
                        Prefs.setThemeMode(context, it)
                    },
                    showPaywall = { showPaywall() },
                    proTab = {
                        when {
                            subscribed -> openUrl(context, FShortsApp.PLAY_SUBSCRIPTIONS)
                            promo -> Toast.makeText(context, R.string.pro_with_code, Toast.LENGTH_SHORT).show()
                            else -> showPaywall()
                        }
                    },
                    restore = restore,
                    manageSubscription = { openUrl(context, FShortsApp.PLAY_SUBSCRIPTIONS) },
                    rateApp = { openUrl(context, "https://play.google.com/store/apps/details?id=${context.packageName}") },
                    redeem = redeem,
                    startUnlockWait = {
                        unlockAt = System.currentTimeMillis() + if (fastTimer) 5_000L else nextWait * 60_000L
                        Prefs.setUnlockAt(context, unlockAt)
                    },
                    cancelUnlockWait = {
                        unlockAt = 0L
                        Prefs.setUnlockAt(context, 0L)
                    },
                    pauseProtection = {
                        unlockAt = 0L
                        Prefs.setUnlockAt(context, 0L)
                        Protection.setPaused(context, true)
                        paused = true
                    },
                    resumeProtection = { minutes ->
                        nextWait = minutes.coerceAtLeast(Protection.MIN_WAIT_MINUTES)
                        Prefs.setNextWaitMinutes(context, nextWait)
                        Protection.setPaused(context, false)
                        paused = false
                    },
                    restartSetup = {
                        rerunSetup = true
                        showSetup = false
                        onboardingKey++
                    },
                ),
            )
        }
      }
      val info = updateInfo
      if (info != null || updateDemo) {
          UpdateDialog(
              onUpdate = {
                  if (updateDemo) {
                      Toast.makeText(context, R.string.update_demo_done, Toast.LENGTH_SHORT).show()
                  } else if (info != null) {
                      (context as? MainActivity)?.let { AppUpdates.start(it, info, it.updateLauncher) }
                  }
                  updateDemo = false
                  updateInfo = null
              },
              onLater = {
                  if (!updateDemo) AppUpdates.snooze(context)
                  updateDemo = false
                  updateInfo = null
              },
          )
      }
    }
}

private fun isServiceEnabled(context: Context): Boolean {
    val component = ComponentName(context, FuckShortsService::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    return enabled.split(':').any {
        it.equals(component.flattenToString(), ignoreCase = true) ||
            it.equals(component.flattenToShortString(), ignoreCase = true)
    }
}

/** Öffnet direkt die Seite von F*ck Shorts in den Bedienungshilfen (spart einen Schritt), sonst die Liste. */
private fun openAccessibilitySettings(context: Context) {
    val component = ComponentName(context, FuckShortsService::class.java).flattenToString()
    val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
        .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
    try {
        context.startActivity(details)
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

private fun openAppInfo(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}

private fun openYouTube(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
    if (intent != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, R.string.youtube_missing, Toast.LENGTH_SHORT).show()
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.link_failed, Toast.LENGTH_SHORT).show()
    }
}
