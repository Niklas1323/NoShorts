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
    var shortsClosed by remember { mutableIntStateOf(Prefs.shortsBlocked(context)) }
    val subscription by Superwall.instance.subscriptionStatus.collectAsState()

    LifecycleResumeEffect(Unit) {
        enabled = isServiceEnabled(context)
        shortsClosed = Prefs.shortsBlocked(context)
        // Zurück aus den Einstellungen und eingeschaltet: Onboarding fertig, danach kommt die Paywall.
        if (enabled && accepted && (!onboardingDone || showSetup)) {
            if (!onboardingDone) {
                Prefs.setOnboardingDone(context)
                onboardingDone = true
                Superwall.instance.register(FShortsApp.PLACEMENT_START)
            }
            showSetup = false
        }
        onPauseOrDispose { }
    }

    // Aus "Turn on protection" kommend: Zurück führt wieder auf die Startseite.
    BackHandler(enabled = showSetup && onboardingDone && accepted) { showSetup = false }

    FShortsTheme(themeMode) {
        if (!onboardingDone || showSetup || !accepted) {
            Onboarding(
                startStep = if (accepted || showSetup || onboardingDone) 2 else 0,
                accepted = accepted,
                onAgree = {
                    Prefs.setDisclosureAccepted(context)
                    accepted = true
                    openAccessibilitySettings(context)
                },
                onOpenSettings = { openAccessibilitySettings(context) },
                onOpenAppInfo = { openAppInfo(context) },
            )
        } else {
            HomeApp(
                HomeState(
                    enabled = enabled,
                    shortsClosed = shortsClosed,
                    isPro = subscription is SubscriptionStatus.Active,
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
                    showPaywall = { Superwall.instance.register(FShortsApp.PLACEMENT_START) },
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
