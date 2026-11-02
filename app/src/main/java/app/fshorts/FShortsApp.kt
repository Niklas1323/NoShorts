package app.fshorts

import android.app.Application
import com.superwall.sdk.Superwall
import com.superwall.sdk.config.options.SuperwallOptions
import com.superwall.sdk.models.entitlements.SubscriptionStatus
import com.superwall.sdk.store.testmode.TestModeBehavior
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class FShortsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Access.load(this)
        // Öffentlicher Schlüssel (pk_), darf in der App stehen. Läuft im selben Prozess wie der Dienst.
        // Testmodus (Kauf nur simuliert, Produkte aus dem Superwall-Dashboard): für alle Test-APKs, solange die Abos
        // in Google Play noch nicht live sind. Release-Builds (Play Store) laufen normal über Google Play.
        val options = SuperwallOptions().apply {
            if (BuildConfig.DEBUG) testModeBehavior = TestModeBehavior.ALWAYS
        }
        Superwall.configure(this, SUPERWALL_KEY, options = options)
        // Abo-Stand merken: Der Dienst braucht ihn sofort, auch bevor Superwall nach einem Neustart geantwortet hat.
        MainScope().launch {
            Superwall.instance.subscriptionStatus.collect { status ->
                when (status) {
                    is SubscriptionStatus.Active -> Access.setSubscribed(this@FShortsApp, true)
                    is SubscriptionStatus.Inactive -> Access.setSubscribed(this@FShortsApp, false)
                    else -> Unit
                }
            }
        }
    }

    companion object {
        const val SUPERWALL_KEY = "pk_FhDD1tEHLAsmd93ianMsW"
        /** Placement im Superwall-Dashboard, an dem die Paywall hängt. */
        const val PLACEMENT_START = "app_start"
        const val PLAY_SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions?sku=fckshorts_pro&package=app.fshorts"
    }
}
