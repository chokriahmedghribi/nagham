package app.nagham.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SourcesScreen(onBack: () -> Unit) {
    val c = LocalApp.current
    var key by remember { mutableStateOf(c.repo.jamendoId) }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            Text("مصادر الموسيقى", style = MaterialTheme.typography.titleLarge)
        }
        Text("Audius", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp))
        Text(
            "مفعّل دائماً. موسيقى مستقلين، مجانية وكاملة.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text("Jamendo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 24.dp, 16.dp, 0.dp))
        Text(
            "أغانٍ كاملة مجانية، وفيها أغانٍ عربية وإصدارات حديثة. للحصول على المفتاح (مجاني):\n" +
                "1) سجّل في devportal.jamendo.com\n2) أنشئ تطبيقاً جديداً\n3) انسخ قيمة Client ID والصقها هنا.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it; saved = false },
            label = { Text("Jamendo Client ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        Button(
            onClick = { c.repo.setJamendoId(key); saved = true },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { Text("حفظ") }
        if (saved) {
            Text(
                if (key.isBlank()) "تم حذف المفتاح." else "تم الحفظ. ارجع للرئيسية لتظهر الأغاني.",
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
