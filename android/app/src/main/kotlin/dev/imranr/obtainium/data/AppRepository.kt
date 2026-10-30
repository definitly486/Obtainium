package dev.imranr.obtainium.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
 private val defaultsAddedKey = booleanPreferencesKey("default_apps_added")
 private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
 private val defaultSources = listOf(
  "https://github.com/barbeau/gpstest/",
  "https://codeberg.org/comaps/comaps/",
  "https://github.com/termux/termux-app",
  "https://github.com/definitly486/BlackviewActive5/",
  "https://github.com/definitly486/Fulguris-fork/",
  "https://github.com/definitly486/File-Manager/",
  "https://github.com/definitly486/comaps/",
  "https://github.com/definitly486/OsmAnd-fork/",
  "https://github.com/definitly486/NewPipe-fork/",
  "https://github.com/definitly486/TemperatureMap-Android./",
  "https://github.com/FossifyOrg/Phone/",
  "https://github.com/FossifyOrg/Contacts/",
  "https://github.com/FossifyOrg/Messages/",
  "https://github.com/definitly486/openboard/",
  "https://github.com/definitly486/ElektrichkiRussia/",
  "https://github.com/definitly486/TrexTrack/",
  "https://github.com/definitly486/vkvideo-tube/",
  "https://github.com/definitly486/gnucash-android/",
  "https://github.com/definitly486/VkDownloader",
  "https://github.com/definitly486/k9-mail"
 )

 val apps: Flow<List<TrackedApp>> = context.appStore.data.map { decode(it[key].orEmpty()) }

 suspend fun ensureDefaultApps() {
  context.appStore.edit { prefs ->
   if (prefs[defaultsAddedKey] != true) {
    val existing = decode(prefs[key].orEmpty()).toMutableList()
    val known = existing.map { canonical(it.sourceUrl) }.toMutableSet()
    defaultSources.forEach { source ->
     if (known.add(canonical(source))) {
      val uri = URI(source)
      val name = uri.path.trim('/').substringAfterLast('/').ifBlank { uri.host }.removeSuffix(".")
      existing += TrackedApp(UUID.randomUUID().toString(), source, name)
     }
    }
    prefs[key] = encode(existing)
    prefs[defaultsAddedKey] = true
   }
  }
 }

 suspend fun add(raw: String): Result<TrackedApp> = withContext(Dispatchers.IO) {
  runCatching {
   val url = raw.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
   val uri = URI(url)
   require(uri.host != null && (uri.scheme == "https" || uri.scheme == "http")) { "Некорректная ссылка" }
   val item = TrackedApp(UUID.randomUUID().toString(), url,
    uri.path.trim('/').substringAfterLast('/').ifBlank { uri.host }.removeSuffix("."))
   save(current().filterNot { canonical(it.sourceUrl) == canonical(url) } + item)
   item
  }
 }

 suspend fun remove(id: String) { save(current().filterNot { it.id == id }) }

 suspend fun check(id: String): Result<TrackedApp> = withContext(Dispatchers.IO) {
  runCatching {
   val old = current().firstOrNull { it.id == id } ?: error("Приложение не найдено")
   val host = URI(old.sourceUrl).host.lowercase()
   val updated = when (host) {
    "github.com", "www.github.com" -> checkGitHub(old)
    "codeberg.org", "www.codeberg.org" -> checkCodeberg(old)
    else -> checkDirect(old)
   }
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
  val (owner, repo) = repositoryPath(app)
  val json = getJson("https://api.github.com/repos/$owner/$repo/releases/latest", "application/vnd.github+json")
  val assets = json.optJSONArray("assets") ?: JSONArray()
  val apk = findApk(assets)
  return app.copy(name = json.optString("name").ifBlank { app.name },
   latestVersion = json.optString("tag_name"), apkUrl = apk,
   lastChecked = System.currentTimeMillis(),
   error = if (apk.isBlank()) "Релиз найден, но APK среди вложений нет" else null)
 }

 private fun checkCodeberg(app: TrackedApp): TrackedApp {
  val (owner, repo) = repositoryPath(app)
  val json = getJson("https://codeberg.org/api/v1/repos/$owner/$repo/releases/latest")
  val assets = json.optJSONArray("assets") ?: JSONArray()
  var apk = ""
  for (i in 0 until assets.length()) {
   val asset = assets.optJSONObject(i) ?: continue
   val name = asset.optString("name")
   val url = asset.optString("browser_download_url").ifBlank { asset.optString("url") }
   if (name.endsWith(".apk", true) || url.endsWith(".apk", true)) { apk = url; break }
  }
  return app.copy(name = json.optString("name").ifBlank { app.name },
   latestVersion = json.optString("tag_name"), apkUrl = apk,
   lastChecked = System.currentTimeMillis(),
   error = if (apk.isBlank()) "Релиз найден, но APK среди вложений нет" else null)
 }

 private fun repositoryPath(app: TrackedApp): Pair<String, String> {
  val p = URI(app.sourceUrl).path.trim('/').split('/')
  require(p.size >= 2) { "Нужна ссылка на репозиторий" }
  return p[0] to p[1].removeSuffix(".")
 }

 private fun getJson(url: String, accept: String? = null): JSONObject {
  val builder = Request.Builder().url(url).header("User-Agent", "Obtainium-Kotlin")
  if (accept != null) builder.header("Accept", accept)
  http.newCall(builder.build()).execute().use { response ->
   if (!response.isSuccessful) error("Ошибка API: HTTP ${response.code} ($url)")
   return JSONObject(response.body?.string().orEmpty())
  }
 }

 private fun findApk(assets: JSONArray): String {
  for (i in 0 until assets.length()) {
   val candidate = assets.optJSONObject(i)?.optString("browser_download_url").orEmpty()
   if (candidate.endsWith(".apk", true)) return candidate
  }
  return ""
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
  context.appStore.edit { it[key] = encode(items) }
 }
 private fun encode(items: List<TrackedApp>): String {
  val json = JSONArray()
  items.forEach { a -> json.put(JSONObject().apply {
   put("id", a.id); put("url", a.sourceUrl); put("name", a.name)
   put("version", a.latestVersion); put("apk", a.apkUrl); put("checked", a.lastChecked)
   put("error", a.error ?: JSONObject.NULL)
  }) }
  return json.toString()
 }
 private fun canonical(url: String) = url.trimEnd('/').lowercase()
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
