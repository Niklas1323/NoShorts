package app.fshorts

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Update-Hinweis über Google Play (In-App-Updates). Gibt es im Play Store eine neuere Version, zeigt die App unsere
 * eigene Meldung; "Jetzt aktualisieren" startet Googles Update-Bildschirm (lädt und installiert, dann Neustart).
 * Funktioniert nur, wenn die App aus dem Play Store installiert ist (Test-APKs von GitHub finden nie ein Update).
 */
object AppUpdates {
    /** "Später" gedrückt: so lange nicht mehr fragen. */
    private const val SNOOZE_MS = 24L * 60 * 60 * 1000

    fun check(context: Context, onAvailable: (AppUpdateInfo) -> Unit) {
        if (System.currentTimeMillis() - Prefs.updateSnoozedAt(context) < SNOOZE_MS) return
        runCatching {
            AppUpdateManagerFactory.create(context).appUpdateInfo.addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) {
                    onAvailable(info)
                }
            }
        }
    }

    /** Ein Update lief schon (App kurz verlassen): Googles Bildschirm wieder zeigen. */
    fun resumeIfRunning(activity: Activity, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        runCatching {
            val manager = AppUpdateManagerFactory.create(activity)
            manager.appUpdateInfo.addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
                }
            }
        }
    }

    fun start(activity: Activity, info: AppUpdateInfo, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        runCatching {
            AppUpdateManagerFactory.create(activity)
                .startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
        }
    }

    fun snooze(context: Context) = Prefs.setUpdateSnoozedAt(context, System.currentTimeMillis())
}
