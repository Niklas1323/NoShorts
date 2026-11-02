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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.fshorts.service.FuckShortsService
import app.fshorts.ui.FShortsTheme
import app.fshorts.ui.HomeActions
import app.fshorts.ui.HomeApp
import app.fshorts.ui.HomeState
import app.fshorts.ui.Onboarding
import com.superwall.sdk.Superwall
import com.superwall.sdk.models.entitlements.SubscriptionStatus
import com.superwall.sdk.paywall.presentation.register

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    var showSetup by remember { mutableStateOf(false) }
    /** In die Android-Einstellungen geschickt: beim Zurückkommen und eingeschaltet geht es weiter. */
    var awaitingEnable by remember { mutableStateOf(false) }
    var onboardingKey by remember { mutableIntStateOf(0) }
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

    /** Einrichtung fertig: zur Startseite, dann die Paywall mit den 3 Gratis-Tagen (ohne sie geht die App nicht). */
    fun finishSetup() {
        awaitingEnable = false
        showSetup = false
        if (!onboardingDone) {
            Prefs.setOnboardingDone(context)
            onboardingDone = true
        }
        if (!Access.unlocked) Superwall.instance.register(FShortsApp.PLACEMENT_START)
    }

    LifecycleResumeEffect(Unit) {
        enabled = isServiceEnabled(context)
        shortsClosed = Prefs.shortsBlocked(context)
        if (enabled && accepted && awaitingEnable) finishSetup()
        onPauseOrDispose { }
    }

    // Aus "Turn on protection" kommend: Zurück führt wieder auf die Startseite.
    BackHandler(enabled = showSetup && onboardingDone && accepted) { showSetup = false }

    FShortsTheme(themeMode) {
        if (!onboardingDone || showSetup || !accepted) {
            key(onboardingKey) {
                Onboarding(
                    startStep = if (showSetup) 2 else 0,
                    accepted = accepted,
                    enabled = enabled,
                    onAgree = {
                        Prefs.setDisclosureAccepted(context)
                        accepted = true
                        if (enabled) {
                            finishSetup()
                        } else {
                            awaitingEnable = true
                            openAccessibilitySettings(context)
                        }
                    },
                    onContinue = { finishSetup() },
                    onOpenSettings = {
                        awaitingEnable = true
                        openAccessibilitySettings(context)
                    },
                    onOpenAppInfo = { openAppInfo(context) },
                )
            }
        } else {
            HomeApp(
                HomeState(
                    enabled = enabled,
                    shortsClosed = shortsClosed,
                    isPro = isPro,
                    promo = promo,
                    themeMode = themeMode,
                    version = BuildConfig.VERSION_NAME,
                ),
                HomeActions(
                    turnOn = { showSetup = true },
                    openYouTube = { openYouTube(context) },
                    setThemeMode = {
                        themeMode = it
                        Prefs.setThemeMode(context, it)
                    },
                    showPaywall = {
                        if (isPro) {
                            Toast.makeText(context, "You already have Pro", Toast.LENGTH_SHORT).show()
                        } else {
                            Superwall.instance.register(FShortsApp.PLACEMENT_START)
                        }
                    },
                    restore = {
                        Superwall.instance.restorePurchases { result ->
                            val text = if (result.isSuccess) "Purchases restored" else "Nothing to restore"
                            ContextCompat.getMainExecutor(context).execute {
                                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    manageSubscription = { openUrl(context, FShortsApp.PLAY_SUBSCRIPTIONS) },
                    rateApp = { openUrl(context, "https://play.google.com/store/apps/details?id=${context.packageName}") },
                    redeem = { code ->
                        when (Access.redeem(context, code)) {
                            Access.Redeem.Unlocked -> {
                                promo = true
                                "Unlocked. F*ck Shorts is free for you."
                            }
                            Access.Redeem.Stopped -> {
                                promo = false
                                "Code removed. Pro now needs a subscription again."
                            }
                            Access.Redeem.Invalid -> "This code isn't valid."
                        }
                    },
                    restartSetup = {
                        Prefs.setOnboardingDone(context, false)
                        onboardingDone = false
                        showSetup = false
                        onboardingKey++
                    },
                ),
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
        Toast.makeText(context, "YouTube isn't installed", Toast.LENGTH_SHORT).show()
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Couldn't open the link", Toast.LENGTH_SHORT).show()
    }
}
