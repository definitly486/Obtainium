package dev.imranr.obtainium.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.imranr.obtainium.data.TrackedApp
import java.text.DateFormat
import java.util.Date

@Composable
fun ObtainiumScreen(model: ObtainiumViewModel) {
 val apps by model.apps.collectAsState()
 var adding by remember { mutableStateOf(false) }
 var url by remember { mutableStateOf("") }
 var error by remember { mutableStateOf<String?>(null) }

 Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
   Text("Obtainium", style = MaterialTheme.typography.headlineMedium)
   Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    OutlinedButton(onClick = model::checkAll, enabled = apps.isNotEmpty()) { Text("Проверить") }
    Button(onClick = { adding = true }) { Text("Добавить") }
   }
  }
  if (apps.isEmpty()) {
   Text("Список пуст. Добавьте ссылку на GitHub-репозиторий или прямой APK URL.")
  } else {
   LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    items(apps, key = { it.id }) { app ->
     AppCard(app, { model.check(app.id) }, { model.remove(app.id) })
    }
   }
  }
 }

 if (adding) AlertDialog(
  onDismissRequest = { adding = false },
  title = { Text("Добавить приложение") },
  text = {
   Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(url, { url = it }, label = { Text("URL репозитория или APK") }, singleLine = true)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
   }
  },
  confirmButton = {
   TextButton(onClick = {
    model.add(url) { result ->
     result.onSuccess { url = ""; error = null; adding = false }
      .onFailure { error = it.message ?: "Не удалось добавить" }
    }
   }) { Text("Добавить") }
  },
  dismissButton = { TextButton(onClick = { adding = false }) { Text("Отмена") } }
 )
}

@Composable
private fun AppCard(app: TrackedApp, onCheck: () -> Unit, onRemove: () -> Unit) {
 Card(Modifier.fillMaxWidth()) {
  Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
   Text(app.name, style = MaterialTheme.typography.titleMedium)
   Text(app.sourceUrl, style = MaterialTheme.typography.bodySmall)
   if (app.latestVersion.isNotBlank()) Text("Версия: ${app.latestVersion}")
   app.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
   if (app.lastChecked > 0) Text(
    "Проверено: ${DateFormat.getDateTimeInstance().format(Date(app.lastChecked))}",
    style = MaterialTheme.typography.bodySmall
   )
   Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = onCheck) { Text("Проверить") }
    TextButton(onClick = onRemove) { Text("Удалить") }
   }
  }
 }
}
