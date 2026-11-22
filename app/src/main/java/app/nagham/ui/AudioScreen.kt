@file:OptIn(ExperimentalMaterial3Api::class)

package app.nagham.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun AudioScreen(onBack: () -> Unit) {
    val c = LocalApp.current
    val eq by c.eq.state.collectAsState()
    val crossfade by c.fade.crossfadeSec.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            Text("إعدادات الصوت", style = MaterialTheme.typography.titleLarge)
        }

        // ---- المعادل ----
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("المعادل (Equalizer)", style = MaterialTheme.typography.titleMedium)
            Switch(checked = eq.enabled, onCheckedChange = { c.eq.setEnabled(it) }, enabled = eq.available)
        }
        if (!eq.available) {
            Text(
                "يصبح المعادل متاحاً بعد تشغيل أي أغنية.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(c.eq.presetNames) { name ->
                    FilterChip(selected = eq.preset == name, onClick = { c.eq.applyPreset(name) }, label = { Text(name) })
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                eq.bands.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (b.centerHz >= 1000) "${b.centerHz / 1000} kHz" else "${b.centerHz} Hz",
                            Modifier.padding(end = 8.dp).then(Modifier),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = b.level.toFloat(),
                            onValueChange = { c.eq.setBand(b.index, it.toInt()) },
                            valueRange = eq.minLevel.toFloat()..eq.maxLevel.toFloat(),
                            enabled = eq.enabled,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (eq.bassSupported) {
                Text("تعزيز الجهير", Modifier.padding(start = 16.dp, top = 12.dp), style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = eq.bassStrength.toFloat(),
                    onValueChange = { c.eq.setBass(it.toInt()) },
                    valueRange = 0f..1000f,
                    enabled = eq.enabled,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // ---- الانتقال المتلاشي ----
        Text(
            "الانتقال بين الأغاني",
            Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (crossfade == 0) "متوقف" else "تلاشي لمدة $crossfade ثانية",
            Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = crossfade.toFloat(),
            onValueChange = { c.fade.setCrossfade(it.toInt()) },
            valueRange = 0f..12f,
            steps = 11,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            "ملاحظة: يخفت صوت الأغنية الحالية في نهايتها ثم يعلو صوت التالية تدريجياً.",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
