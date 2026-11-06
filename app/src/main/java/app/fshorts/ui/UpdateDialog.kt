package app.fshorts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fshorts.R

/** Schräger Verlauf: oben links schwarz, unten rechts rot (wie die Paywall, nur diagonal). */
private val diagonalGradient = Brush.linearGradient(
    0f to Color(0xFF0B0B0D),
    0.35f to Color(0xFF2A080B),
    0.7f to Color(0xFF8E1219),
    1f to Color(0xFFC8161F),
    start = Offset.Zero,
    end = Offset.Infinite,
)

/** Meldung "Update verfügbar". */
@Composable
fun UpdateDialog(onUpdate: () -> Unit, onLater: () -> Unit) {
    Dialog(onDismissRequest = onLater, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(diagonalGradient)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandLogo(56.dp)
                Spacer(Modifier.size(14.dp))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.ArrowDownward, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(stringResource(R.string.update_title), style = headline(32.sp), color = Color.White, lineHeight = 36.sp)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.update_text), color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
            Spacer(Modifier.height(26.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(29.dp))
                    .background(Color.White)
                    .clickable(onClick = onUpdate),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.update_now), color = Brand.RedText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.update_later),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onLater)
                    .padding(12.dp),
            )
        }
    }
}
