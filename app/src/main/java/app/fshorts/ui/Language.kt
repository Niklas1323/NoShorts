package app.fshorts.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fshorts.AppLanguage
import app.fshorts.R

/** Kleiner Knopf "🇺🇸 English ▾" oben auf der Willkommensseite. */
@Composable
fun LanguagePill() {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable { open = true }
            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(current.flag, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(current.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.language), tint = Color.White)
    }
    if (open) LanguageSheet(dark = true, onClose = { open = false })
}

/** Liste aller Sprachen mit Flagge. Antippen = wechseln (die Seite baut sich in der neuen Sprache neu auf). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(dark: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    val text = if (dark) Color.White else LocalPalette.current.text
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (dark) Color(0xFF17171A) else Color(0xFFF4F4F6),
    ) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.language),
                style = headline(28.sp),
                color = text,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            AppLanguage.ALL.forEach { lang ->
                val selected = lang.code == current.code
                val shape = RoundedCornerShape(18.dp)
                Row(
                    Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(text.copy(alpha = if (selected) 0.14f else 0.05f))
                        .clickable {
                            onClose()
                            (context as? Activity)?.let { AppLanguage.set(it, lang.code) }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(lang.flag, fontSize = 24.sp)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        lang.name,
                        color = text,
                        fontSize = 17.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(Icons.Filled.Check, null, tint = text, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
