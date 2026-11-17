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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fshorts.R
import app.fshorts.service.BarPreview
import kotlin.math.roundToInt

/**
 * Die Leiste so, wie sie in YouTube aussieht, verkleinert auf die verfügbare Breite. Gezeichnet wird sie in Handy-Breite
 * (360 dp), damit Symbole und Schrift im selben Verhältnis stehen wie in YouTube.
 */
@Composable
fun ScaledBarPreview(modern: Boolean, dark: Boolean, modifier: Modifier = Modifier) {
    Layout(content = { BarPreview(modern = modern, dark = dark) }, modifier = modifier.clipToBounds()) { measurables, constraints ->
        val vw = 360.dp.roundToPx()
        val vh = 64.dp.roundToPx()
        val placeable = measurables.first().measure(Constraints.fixed(vw, vh))
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else vw
        val s = w.toFloat() / vw
        layout(w, (vh * s).roundToInt()) {
            placeable.placeWithLayer(0, 0) {
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/** Einstellungen: eine Wahl (klassisch oder modern) mit grosser Vorschau, Titel und Beschreibung. */
@Composable
fun BarStyleOption(modern: Boolean, selected: Boolean, title: String, description: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Brand.Red else p.cardBorder, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
    ) {
        ScaledBarPreview(
            modern = modern,
            dark = p.dark,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioDot(selected)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(description, color = p.subtext, fontSize = 13.sp)
            }
        }
    }
}

/** Startseite: kleines Widget, Leisten-Stil auf die Schnelle wählen (zwei Bildchen nebeneinander). */
@Composable
fun BarStyleWidget(modern: Boolean, onSelect: (Boolean) -> Unit, dense: Boolean = false) {
    val p = LocalPalette.current
    GlassCard(padding = if (dense) 8.dp else 12.dp) {
        // Wenig Platz: ohne Überschrift (die Bildchen und Namen sagen genug).
        if (!dense) {
            Text(stringResource(R.string.bar_widget), color = p.subtext, fontSize = 13.sp, modifier = Modifier.padding(start = 2.dp))
            Spacer(Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BarStyleTile(false, !modern, stringResource(R.string.bar_classic), Modifier.weight(1f)) { onSelect(false) }
            BarStyleTile(true, modern, stringResource(R.string.bar_modern), Modifier.weight(1f)) { onSelect(true) }
        }
    }
}

@Composable
private fun BarStyleTile(modern: Boolean, selected: Boolean, label: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Brand.Red else p.cardBorder, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScaledBarPreview(modern = modern, dark = p.dark, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)))
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (selected) p.text else p.subtext,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) Brand.Red else p.subtext, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(12.dp).clip(CircleShape).background(Brand.Red))
    }
}
