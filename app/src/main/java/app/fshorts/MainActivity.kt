package app.fshorts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.fshorts.service.FuckShortsService
import app.fshorts.service.TreeDumper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                MainScreen()
            }
        }
    }
}

private val Red = Color(0xFFFF3B30)

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Red, background = Color(0xFF0F0F0F), surface = Color(0xFF0F0F0F))
    } else {
        lightColorScheme(primary = Color(0xFFD70015))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isServiceEnabled(context)) }
    var accepted by remember { mutableStateOf(Prefs.disclosureAccepted(context)) }
    var debug by remember { mutableStateOf(Prefs.debugDumps(context)) }
    var dumpCount by remember { mutableIntStateOf(TreeDumper.files(context).size) }
    var blocked by remember { mutableIntStateOf(Prefs.shortsBlocked(context)) }
    var autoHide by remember { mutableStateOf(Prefs.autoHideShelf(context)) }

    LifecycleResumeEffect(Unit) {
        enabled = isServiceEnabled(context)
        dumpCount = TreeDumper.files(context).size
        blocked = Prefs.shortsBlocked(context)
        onPauseOrDispose { }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Fuck Shorts", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text(
                "Lange Videos ja. Shorts nein.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StatusCard(enabled, blocked)

            if (!enabled) {
                if (!accepted) {
                    DisclosureCard(onAccept = {
                        Prefs.setDisclosureAccepted(context)
                        accepted = true
                    })
                } else {
                    SetupCard(context)
                }
            } else {
                Button(onClick = { openYouTube(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("YouTube öffnen")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Shorts-Regal wegklicken", fontWeight = FontWeight.Bold)
                        Text(
                            "Die App tippt beim Shorts-Regal auf ⋮ → \"Nicht interessiert\". " +
                                "Dann blendet YouTube das Regal selbst aus.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(checked = autoHide, onCheckedChange = {
                        autoHide = it
                        Prefs.setAutoHideShelf(context, it)
                    })
                }
            }

            DebugCard(
                debug = debug,
                dumpCount = dumpCount,
                onDebugChange = {
                    debug = it
                    Prefs.setDebugDumps(context, it)
                },
                onShare = { shareDumps(context) },
                onClear = {
                    TreeDumper.clear(context)
                    dumpCount = 0
                },
            )
        }
    }
}

@Composable
private fun StatusCard(enabled: Boolean, blocked: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color(0xFF1B5E20) else Color(0xFF7F1D1D),
            contentColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (enabled) "Schutz ist AN" else "Schutz ist AUS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (enabled) {
                    "Öffne YouTube. Unten gibt es keinen Shorts-Knopf mehr."
                } else {
                    "YouTube zeigt gerade noch Shorts. Schalte Fuck Shorts unten ein."
                },
            )
            if (blocked > 0) Text("Shorts weggeklickt: $blocked")
        }
    }
}

@Composable
private fun DisclosureCard(onAccept: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bevor es losgeht", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Damit Fuck Shorts funktioniert, braucht die App die Berechtigung \"Bedienungshilfen\".",
            )
            Text("Damit kann die App:")
            Text("• sehen, wo in der YouTube-App welche Knöpfe sind")
            Text("• eine eigene Leiste ohne Shorts darüberlegen")
            Text("• \"Zurück\" drücken, wenn ein Short aufgeht")
            Text("Was die App NICHT macht:")
            Text("• Sie liest keine anderen Apps.")
            Text("• Sie liest keine Passwörter oder Nachrichten.")
            Text("• Sie schickt nichts ins Internet.")
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text("Verstanden, weiter")
            }
        }
    }
}

@Composable
private fun SetupCard(context: Context) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("So schaltest du es ein", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("1. Tippe auf \"Bedienungshilfen öffnen\".")
            Text("2. Schalte \"Fuck Shorts\" ein.")
            Button(onClick = { openAccessibilitySettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Bedienungshilfen öffnen")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(
                    "Ist der Schalter grau oder kommt \"Eingeschränkte Einstellung\"? Das ist normal bei Apps, " +
                        "die nicht aus dem Play Store kommen. Dann:",
                )
                Text("3. Tippe auf \"App-Info öffnen\".")
                Text("4. Oben rechts auf ⋮ tippen → \"Eingeschränkte Einstellungen zulassen\".")
                Text("5. Danach nochmal Schritt 1 und 2.")
                OutlinedButton(onClick = { openAppInfo(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("App-Info öffnen")
                }
            }
        }
    }
}

@Composable
private fun DebugCard(
    debug: Boolean,
    dumpCount: Int,
    onDebugChange: (Boolean) -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Für Fehlersuche", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Wenn etwas nicht klappt, speichert die App den Aufbau der YouTube-Seite " +
                    "(nur Knöpfe, Namen und Positionen). Du kannst auch lange auf unsere Leiste drücken, " +
                    "um den aktuellen Bildschirm zu speichern.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bei jedem Bildschirmwechsel speichern", modifier = Modifier.weight(1f))
                Switch(checked = debug, onCheckedChange = onDebugChange)
            }
            Text("Gespeichert: $dumpCount", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onShare, enabled = dumpCount > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Struktur teilen")
            }
            OutlinedButton(onClick = onClear, enabled = dumpCount > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Löschen")
            }
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

/** Öffnet direkt die Seite von Fuck Shorts in den Bedienungshilfen (spart einen Schritt), sonst die Liste. */
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
        Toast.makeText(context, "YouTube ist nicht installiert", Toast.LENGTH_SHORT).show()
    }
}

private fun shareDumps(context: Context) {
    val files = TreeDumper.files(context)
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) })
    val send = Intent(Intent.ACTION_SEND_MULTIPLE)
        .setType("*/*")
        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        .putExtra(Intent.EXTRA_SUBJECT, "Fuck Shorts – Struktur")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Struktur teilen"))
}
