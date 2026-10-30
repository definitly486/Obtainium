package dev.imranr.obtainium.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

private val Context.appStore by preferencesDataStore("tracked_apps")

class AppRepository(private val context: Context) {
 private val key = stringPreferencesKey("apps")
 private val http = OkHttpClient.Builder()
  .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

 val apps: Flow<List<TrackedApp>> = context.appStore.data.map { decode(it[key].orEmpty()) }

 suspend fun add(raw: String): Result<TrackedApp> = withContext(Dispatchers.IO) {
  runCatching {
   val url = raw.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
   val uri = URI(url)
   require(uri.host != null && (uri.scheme == "https" || uri.scheme == "http")) { "Некорректная ссылка" }
   val item = TrackedApp(UUID.randomUUID().toString(), url,
    uri.path.trim('/').substringAfterLast('/').ifBlank { uri.host })
   save(current().filterNot { it.sourceUrl == url } + item)
   item
  }
 }

 suspend fun remove(id: String) { save(current().filterNot { it.id == id }) }

 suspend fun check(id: String): Result<TrackedApp> = withContext(Dispatchers.IO) {
  runCatching {
   val old = current().firstOrNull { it.id == id } ?: error("Приложение не найдено")
   val updated = if (URI(old.sourceUrl).host.equals("github.com", true)) checkGitHub(old) else checkDirect(old)
   save(current().map { if (it.id == id) updated else it })
   updated
  }.onFailure { e ->
   current().firstOrNull { it.id == id }?.let { old ->
    save(current().map { if (it.id == id) old.copy(error = e.message ?: "Ошибка") else it })
   }
  }
 }

 suspend fun checkAll() { current().forEach { check(it.id) } }

 private fun checkGitHub(app: TrackedApp): TrackedApp {
  val p = URI(app.sourceUrl).path.trim('/').split('/')
  require(p.size >= 2) { "Нужна ссылка на GitHub repository" }
  val req = Request.Builder()
   .url("https://api.github.com/repos/${p[0]}/${p[1]}/releases/latest")
   .header("Accept", "application/vnd.github+json")
   .header("User-Agent", "Obtainium-Kotlin").build()
  http.newCall(req).execute().use { response ->
   if (!response.isSuccessful) error("GitHub API HTTP ${response.code}")
   val json = JSONObject(response.body?.string().orEmpty())
   val assets = json.optJSONArray("assets") ?: JSONArray()
   var apk = ""
   for (i in 0 until assets.length()) {
    val candidate = assets.getJSONObject(i).optString("browser_download_url")
    if (candidate.endsWith(".apk", true)) { apk = candidate; break }
   }
   return app.copy(name = json.optString("name").ifBlank { app.name },
    latestVersion = json.optString("tag_name"), apkUrl = apk,
    lastChecked = System.currentTimeMillis(),
    error = if (apk.isBlank()) "APK в последнем релизе не найден" else null)
  }
 }

 private fun checkDirect(app: TrackedApp): TrackedApp {
  val req = Request.Builder().url(app.sourceUrl).head().header("User-Agent", "Obtainium-Kotlin").build()
  http.newCall(req).execute().use { response ->
   if (!response.isSuccessful) error("HTTP ${response.code}")
   val version = response.header("ETag") ?: response.header("Last-Modified")
    ?: response.header("Content-Length")?.let { "$it bytes" } ?: "Доступен"
   return app.copy(latestVersion = version, apkUrl = response.request.url.toString(),
    lastChecked = System.currentTimeMillis(), error = null)
  }
 }

 private suspend fun current(): List<TrackedApp> =
  context.appStore.data.map { decode(it[key].orEmpty()) }.first()

 private suspend fun save(items: List<TrackedApp>) {
  val json = JSONArray()
  items.forEach { a -> json.put(JSONObject().apply {
   put("id", a.id); put("url", a.sourceUrl); put("name", a.name)
   put("version", a.latestVersion); put("apk", a.apkUrl); put("checked", a.lastChecked)
   put("error", a.error ?: JSONObject.NULL)
  }) }
  context.appStore.edit { it[key] = json.toString() }
 }

 private fun decode(raw: String): List<TrackedApp> = runCatching {
  val arr = JSONArray(raw)
  (0 until arr.length()).map { i ->
   val o = arr.getJSONObject(i)
   TrackedApp(o.getString("id"), o.getString("url"), o.optString("name"),
    o.optString("version"), o.optString("apk"), o.optLong("checked"),
    o.optString("error").takeUnless { it.isBlank() || it == "null" })
  }
 }.getOrDefault(emptyList())
}
