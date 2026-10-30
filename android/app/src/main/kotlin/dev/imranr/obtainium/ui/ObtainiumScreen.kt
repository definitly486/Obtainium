package dev.imranr.obtainium.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import coil.compose.AsyncImage
import dev.imranr.obtainium.data.TrackedApp
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun ObtainiumScreen(model: ObtainiumViewModel) {
 val apps by model.apps.collectAsState()
 val githubToken by model.githubToken.collectAsState()
 val downloadProgress by model.downloadProgress.collectAsState()
 val context = LocalContext.current
 var adding by remember { mutableStateOf(false) }
 var url by remember { mutableStateOf("") }
 var error by remember { mutableStateOf<String?>(null) }
 var tokenDialog by remember { mutableStateOf(false) }
 var tokenDraft by remember { mutableStateOf("") }
 var editingApp by remember { mutableStateOf<TrackedApp?>(null) }
 var editUrl by remember { mutableStateOf("") }
 var editName by remember { mutableStateOf("") }
 var editError by remember { mutableStateOf<String?>(null) }

 Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
   Text("Obtainium", style = MaterialTheme.typography.headlineMedium)
   Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    OutlinedButton(onClick = model::checkAll, enabled = apps.isNotEmpty()) { Text("Проверить") }
    OutlinedButton(onClick = { tokenDraft = githubToken; tokenDialog = true }) { Text("PAT") }
    Button(onClick = { adding = true }) { Text("Добавить") }
   }
  }
  if (apps.isEmpty()) {
   Text("Список пуст. Добавьте ссылку на GitHub-репозиторий или прямой APK URL.")
  } else {
   LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    items(apps, key = { it.id }) { app ->
     AppCard(app, githubToken, downloadProgress?.takeIf { it.appId == app.id },
      onCheck = { model.check(app.id) },
      onRemove = { model.remove(app.id) },
      onEdit = {
       editingApp = app
       editUrl = app.sourceUrl
       editName = app.name
       editError = null
      },
      onInstall = {
       model.install(app) { result ->
        result.onSuccess {
         Toast.makeText(context, "APK загружен. Подтвердите установку в системном окне.", Toast.LENGTH_LONG).show()
        }.onFailure {
         Toast.makeText(context, it.message ?: "Ошибка загрузки или установки", Toast.LENGTH_LONG).show()
        }
       }
      })
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

 if (editingApp != null) AlertDialog(
  onDismissRequest = { editingApp = null },
  title = { Text("Редактировать приложение") },
  text = {
   Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(value = editName, onValueChange = { editName = it },
     label = { Text("Название") }, singleLine = true)
    OutlinedTextField(value = editUrl, onValueChange = { editUrl = it },
     label = { Text("URL репозитория, релиза или APK") }, singleLine = true)
    editError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Text("При изменении URL данные о версии и APK будут сброшены.", style = MaterialTheme.typography.bodySmall)
   }
  },
  confirmButton = {
   TextButton(onClick = {
    val selected = editingApp ?: return@TextButton
    model.update(selected.id, editUrl, editName) { result ->
     result.onSuccess { editingApp = null }
      .onFailure { editError = it.message ?: "Не удалось сохранить изменения" }
    }
   }) { Text("Сохранить") }
  },
  dismissButton = { TextButton(onClick = { editingApp = null }) { Text("Отмена") } }
 )

 if (tokenDialog) AlertDialog(
  onDismissRequest = { tokenDialog = false },
  title = { Text("GitHub Personal Access Token") },
  text = {
   Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Токен используется для запросов GitHub API и поиска иконок. Оставьте поле пустым, чтобы удалить токен.")
    OutlinedTextField(
     value = tokenDraft,
     onValueChange = { tokenDraft = it },
     label = { Text("Personal access token") },
     singleLine = true,
     visualTransformation = PasswordVisualTransformation()
    )
   }
  },
  confirmButton = {
   TextButton(onClick = {
    model.saveGitHubToken(tokenDraft)
    tokenDialog = false
   }) { Text("Сохранить") }
  },
  dismissButton = { TextButton(onClick = { tokenDialog = false }) { Text("Отмена") } }
 )

}

@Composable
private fun AppCard(app: TrackedApp, githubToken: String, progress: ApkDownloadProgress?, onCheck: () -> Unit, onRemove: () -> Unit, onEdit: () -> Unit, onInstall: () -> Unit) {
 val context = LocalContext.current
 val iconPath by produceState<String?>(initialValue = null, app.sourceUrl, githubToken) {
  value = findLauncherIcon(context, app.sourceUrl, githubToken)
 }
 Card(Modifier.fillMaxWidth()) {
  Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
   Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    if (iconPath != null) {
     AsyncImage(
      model = File(iconPath!!),
      contentDescription = "Иконка ${app.name}",
      modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
      contentScale = ContentScale.Crop
     )
    } else {
     Box(
      modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
       .background(MaterialTheme.colorScheme.secondaryContainer),
      contentAlignment = Alignment.Center
     ) {
      Text(app.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium,
       color = MaterialTheme.colorScheme.onSecondaryContainer)
     }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
     Text(app.name, style = MaterialTheme.typography.titleMedium)
     Text(app.sourceUrl, style = MaterialTheme.typography.bodySmall)
    }
   }
   if (app.latestVersion.isNotBlank()) Text("Версия: ${app.latestVersion}")
   app.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
   if (app.lastChecked > 0) Text(
    "Проверено: ${DateFormat.getDateTimeInstance().format(Date(app.lastChecked))}",
    style = MaterialTheme.typography.bodySmall
   )
   Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = onCheck) { Text("Проверить") }
    OutlinedButton(onClick = onEdit) { Text("Редактировать") }
   }
   if (progress != null) {
    val knownTotal = progress.total > 0L
    val fraction = if (knownTotal) (progress.downloaded.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f) else 0f
    if (knownTotal) {
     LinearProgressIndicator(
      progress = { fraction },
      modifier = Modifier.fillMaxWidth()
     )
    } else {
     LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    val downloadedText = formatBytes(progress.downloaded)
    val progressText = if (knownTotal) {
     "$downloadedText / ${formatBytes(progress.total)} · ${(fraction * 100).toInt()}%"
    } else "$downloadedText · загрузка"
    Text(progressText, style = MaterialTheme.typography.bodySmall)
   }
   Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(onClick = onInstall, enabled = app.apkUrl.isNotBlank() && progress == null) {
     Text(if (progress != null) "Загрузка…" else "Скачать и установить")
    }
    TextButton(onClick = onRemove) { Text("Удалить") }
   }
  }
 }
}


private fun formatBytes(bytes: Long): String {
 if (bytes < 1024L) return "$bytes Б"
 val kb = bytes / 1024.0
 if (kb < 1024.0) return String.format(java.util.Locale.getDefault(), "%.1f КБ", kb)
 val mb = kb / 1024.0
 if (mb < 1024.0) return String.format(java.util.Locale.getDefault(), "%.1f МБ", mb)
 return String.format(java.util.Locale.getDefault(), "%.2f ГБ", mb / 1024.0)
}
