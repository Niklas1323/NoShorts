package app.fshorts

import android.app.Application
import com.superwall.sdk.Superwall

class FShortsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Öffentlicher Schlüssel (pk_), darf in der App stehen. Läuft im selben Prozess wie der Dienst, stört ihn aber nicht.
        Superwall.configure(this, SUPERWALL_KEY)
    }

    companion object {
        const val SUPERWALL_KEY = "pk_TuWN1bNCyvooyXGoe-UMU"
        /** Placement im Superwall-Dashboard, an dem die Paywall hängt. */
        const val PLACEMENT_START = "app_start"
        const val PLAY_SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions?sku=fckshorts_pro&package=app.fshorts"
    }
}
