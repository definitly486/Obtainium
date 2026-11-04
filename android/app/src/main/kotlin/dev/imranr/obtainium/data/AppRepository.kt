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
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

private val Context.appStore by preferencesDataStore("tracked_apps")

class AppRepository(private val context: Context) {
 private val key = stringPreferencesKey("apps")
 private val defaultsAddedKey = booleanPreferencesKey("default_apps_added")
 private val githubTokenKey = stringPreferencesKey("github_personal_access_token")
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
  "https://github.com/definitly486/TemperatureMap-Android/",
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
 val githubToken: Flow<String> = context.appStore.data.map { it[githubTokenKey].orEmpty() }

 suspend fun setGitHubToken(token: String) {
  context.appStore.edit { prefs ->
   val cleaned = token.trim()
   if (cleaned.isBlank()) prefs.remove(githubTokenKey) else prefs[githubTokenKey] = cleaned
  }
 }

 
 /**
  * Downloads and decrypts an OpenSSL enc file generated with:
  * aes-256-cbc -pbkdf2 -iter 200000 (PBKDF2-HMAC-SHA256).
  * The passphrase and token are kept only in memory.
  */
 suspend fun importEncryptedGitHubToken(passphrase: String) = withContext(Dispatchers.IO) {
  require(passphrase.isNotEmpty()) { "Введите пароль расшифровки" }
  val request = Request.Builder()
   .url("https://raw.githubusercontent.com/definitly486/Obtainium/main/api.github.obtainium.enc")
   .header("Accept", "application/octet-stream")
   .header("User-Agent", "Obtainium-Kotlin")
   .build()
  val encrypted = http.newCall(request).execute().use { response ->
   if (!response.isSuccessful) error("Не удалось загрузить зашифрованный PAT: HTTP ${response.code}")
   response.body?.bytes() ?: error("Пустой ответ сервера")
  }
  val prefix = "Salted__".toByteArray(Charsets.US_ASCII)
  require(encrypted.size >= 32 && prefix.indices.all { encrypted[it] == prefix[it] }) {
   "Неверный формат зашифрованного файла"
  }
  val salt = encrypted.copyOfRange(8, 16)
  val ciphertext = encrypted.copyOfRange(16, encrypted.size)
  require(ciphertext.isNotEmpty() && ciphertext.size % 16 == 0) {
   "Повреждённый зашифрованный файл"
  }

  val passwordBytes = passphrase.toByteArray(Charsets.UTF_8)
  val keyAndIv = try {
   pbkdf2Sha256(passwordBytes, salt, 200_000, 48)
  } finally {
   passwordBytes.fill(0)
  }
  val plaintext = try {
   val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
   cipher.init(
    javax.crypto.Cipher.DECRYPT_MODE,
    javax.crypto.spec.SecretKeySpec(keyAndIv.copyOfRange(0, 32), "AES"),
    javax.crypto.spec.IvParameterSpec(keyAndIv.copyOfRange(32, 48))
   )
   cipher.doFinal(ciphertext)
  } finally {
   keyAndIv.fill(0)
  }
  val token = try {
   plaintext.toString(Charsets.UTF_8).trim()
  } finally {
   plaintext.fill(0)
  }
  require(token.isNotBlank() && !token.any { it.isWhitespace() }) {
   "Не удалось расшифровать PAT. Проверьте пароль."
  }
  setGitHubToken(token)
 }

 private fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
  val mac = javax.crypto.Mac.getInstance("HmacSHA256")
  mac.init(javax.crypto.spec.SecretKeySpec(password, "HmacSHA256"))
  val hLen = mac.macLength
  val blocks = (length + hLen - 1) / hLen
  val output = ByteArray(blocks * hLen)
  for (block in 1..blocks) {
   val input = salt + byteArrayOf(
    (block ushr 24).toByte(), (block ushr 16).toByte(),
    (block ushr 8).toByte(), block.toByte()
   )
   var u = mac.doFinal(input)
   val t = u.copyOf()
   for (i in 1 until iterations) {
    u = mac.doFinal(u)
    for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
   }
   t.copyInto(output, (block - 1) * hLen)
   u.fill(0)
   t.fill(0)
  }
  return output.copyOf(length).also { output.fill(0) }
 }

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
   val segments = uri.path.split('/').filter { it.isNotBlank() }
   val isGitHubReleaseUrl = uri.host?.lowercase() in setOf("github.com", "www.github.com") &&
    segments.size >= 5 && segments[2].equals("releases", ignoreCase = true) &&
    segments[3].equals("tag", ignoreCase = true)
   val appName = if (isGitHubReleaseUrl) segments[1] else
    segments.lastOrNull().orEmpty().ifBlank { uri.host }.removeSuffix(".")
   val item = TrackedApp(UUID.randomUUID().toString(), url, appName)
   save(current().filterNot { canonical(it.sourceUrl) == canonical(url) } + item)
   item
  }
 }



 suspend fun exportAppsJson(): String = withContext(Dispatchers.IO) {
  JSONArray().also { array -> current().forEach { app ->
   array.put(JSONObject().apply {
    put("id", app.id); put("url", app.sourceUrl); put("name", app.name)
    put("version", app.latestVersion); put("apk", app.apkUrl); put("checked", app.lastChecked)
    put("error", app.error ?: JSONObject.NULL); put("packageName", app.packageName)
   })
  } }.toString(2)
 }

 suspend fun importAppsJson(raw: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
  val array = JSONArray(raw)
  val existing = current().toMutableList()
  val known = existing.map { canonical(it.sourceUrl) }.toMutableSet()
  var added = 0
  for (i in 0 until array.length()) {
   val item = array.optJSONObject(i) ?: continue
   val url = item.optString("url").trim()
   if (url.isBlank() || !known.add(canonical(url))) continue
   val parsed = runCatching {
    val uri = URI(url)
    require(uri.host != null && uri.scheme in setOf("http", "https"))
    TrackedApp(item.optString("id").takeIf { candidate -> candidate.isNotBlank() && existing.none { it.id == candidate } } ?: UUID.randomUUID().toString(), url,
     item.optString("name").ifBlank { uri.host.orEmpty() }, item.optString("version"),
     item.optString("apk"), item.optLong("checked"),
     item.optString("error").takeUnless { it.isBlank() || it == "null" },
     item.optString("packageName"))
   }.getOrNull() ?: continue
   existing.add(parsed)
   added++
  }
  if (added > 0) save(existing)
  added to array.length()
 }

 suspend fun addUrlList(raw: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
  val lines = raw.lineSequence().map { it.trim() }
   .filter { it.isNotBlank() && !it.startsWith("#") }
   .map { it.substringBefore(" ").trim() }.distinct().toList()
  val existing = current().toMutableList()
  val known = existing.map { canonical(it.sourceUrl) }.toMutableSet()
  var added = 0
  for (line in lines) {
   val normalized = line.let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
   val valid = runCatching { URI(normalized).let { it.host != null && it.scheme in setOf("http", "https") } }.getOrDefault(false)
   if (!valid || !known.add(canonical(normalized))) continue
   val uri = URI(normalized)
   val segments = uri.path.split('/').filter { it.isNotBlank() }
   val name = if (uri.host.equals("github.com", true) && segments.size >= 2) segments[1]
    else segments.lastOrNull().orEmpty().ifBlank { uri.host.orEmpty() }
   existing.add(TrackedApp(UUID.randomUUID().toString(), normalized, name))
   added++
  }
  if (added > 0) save(existing)
  added to lines.size
 }

 suspend fun update(id: String, rawUrl: String, rawName: String): Result<TrackedApp> = withContext(Dispatchers.IO) {
  runCatching {
   val url = rawUrl.trim().let {
    if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
   }
   val uri = URI(url)
   require(uri.host != null && uri.scheme in setOf("http", "https")) { "Некорректная ссылка" }
   val old = current().firstOrNull { it.id == id } ?: error("Приложение не найдено")
   require(current().none { it.id != id && canonical(it.sourceUrl) == canonical(url) }) {
    "Такая ссылка уже добавлена"
   }
   val name = rawName.trim().ifBlank {
    val parts = uri.path.split('/').filter { it.isNotBlank() }
    if (uri.host?.lowercase() in setOf("github.com", "www.github.com") && parts.size >= 2) parts[1]
    else parts.lastOrNull() ?: uri.host.orEmpty()
   }
   val changedSource = canonical(old.sourceUrl) != canonical(url)
   val updated = old.copy(
    sourceUrl = url,
    name = name,
    latestVersion = if (changedSource) "" else old.latestVersion,
    apkUrl = if (changedSource) "" else old.apkUrl,
    lastChecked = if (changedSource) 0L else old.lastChecked,
    error = null
   )
   save(current().map { if (it.id == id) updated else it })
   updated
  }
 }

 suspend fun setPackageName(id: String, packageName: String) {
  val cleaned = packageName.trim()
  if (cleaned.isBlank()) return
  save(current().map { if (it.id == id) it.copy(packageName = cleaned) else it })
 }

 suspend fun remove(id: String) { save(current().filterNot { it.id == id }) }

 suspend fun check(id: String): Result<TrackedApp> = withContext(Dispatchers.IO) {
  runCatching {
   val old = current().firstOrNull { it.id == id } ?: error("Приложение не найдено")
   val token = context.appStore.data.map { it[githubTokenKey].orEmpty() }.first()
   val host = URI(old.sourceUrl).host.lowercase()
   val updated = when (host) {
    "github.com", "www.github.com" -> checkGitHub(old, token)
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

 private fun checkGitHub(app: TrackedApp, token: String): TrackedApp {
  val uri = URI(app.sourceUrl)
  val segments = uri.path.split('/').filter { it.isNotBlank() }
  require(segments.size >= 2) { "Нужна ссылка на GitHub-репозиторий или APK" }
  val owner = segments[0]
  val repo = segments[1].removeSuffix(".")

  // A direct GitHub release asset URL can be used as the app source:
  // /owner/repo/releases/download/<tag>/<asset>.apk
  if (segments.size >= 6 && segments[2] == "releases" &&
   segments[3] == "download" && segments.last().substringBefore('?').endsWith(".apk", true)) {
   val tag = segments[4]
   return app.copy(latestVersion = tag, apkUrl = app.sourceUrl,
    lastChecked = System.currentTimeMillis(), error = null)
  }

  val releaseTag = if (segments.size >= 5 &&
   segments[2] == "releases" && segments[3] == "tag") segments.drop(4).joinToString("/") else null

  val release = if (!releaseTag.isNullOrBlank()) {
   val encodedTag = URLEncoder.encode(releaseTag, "UTF-8").replace("+", "%20")
   getJson("https://api.github.com/repos/$owner/$repo/releases/tags/$encodedTag",
    "application/vnd.github+json", token)
  } else {
   try {
    getJson("https://api.github.com/repos/$owner/$repo/releases/latest",
     "application/vnd.github+json", token)
   } catch (e: Exception) {
    // /releases/latest excludes prereleases. Many projects publish APKs
    // only in GitHub Actions prereleases, so inspect the recent releases too.
    if (!e.message.orEmpty().contains("HTTP 404")) throw e
    val list = getJsonArray("https://api.github.com/repos/$owner/$repo/releases?per_page=30", token)
    var found: JSONObject? = null
    for (i in 0 until list.length()) {
     val candidate = list.optJSONObject(i) ?: continue
     if (candidate.optBoolean("draft")) continue
     if (findApk(candidate.optJSONArray("assets") ?: JSONArray()).isNotBlank()) {
      found = candidate
      break
     }
    }
    found ?: throw IllegalStateException("В последних релизах GitHub APK не найден")
   }
  }

  val apk = findApk(release.optJSONArray("assets") ?: JSONArray())
  return app.copy(name = release.optString("name").ifBlank { app.name },
   latestVersion = release.optString("tag_name"), apkUrl = apk,
   lastChecked = System.currentTimeMillis(),
   error = if (apk.isBlank()) "Релиз найден, но APK среди вложений нет" else null)
 }

 private fun getJsonArray(url: String, token: String = ""): JSONArray {
  val builder = Request.Builder().url(url).header("User-Agent", "Obtainium-Kotlin")
   .header("Accept", "application/vnd.github+json")
  if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
  http.newCall(builder.build()).execute().use { response ->
   if (!response.isSuccessful) error("Ошибка API: HTTP ${response.code} ($url).")
   return JSONArray(response.body?.string().orEmpty())
  }
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
  val p = URI(app.sourceUrl).path.split('/').filter { it.isNotBlank() }
  require(p.size >= 2) { "Нужна ссылка на GitHub-репозиторий" }
  require(p[0] !in setOf("login", "settings", "explore")) { "Ссылка не является ссылкой на репозиторий GitHub" }
  return p[0] to p[1].removeSuffix(".")
 }

 private fun getJson(url: String, accept: String? = null, token: String = ""): JSONObject {
  val builder = Request.Builder().url(url).header("User-Agent", "Obtainium-Kotlin")
  if (accept != null) builder.header("Accept", accept)
  if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
  http.newCall(builder.build()).execute().use { response ->
   if (!response.isSuccessful) {
    val remaining = response.header("X-RateLimit-Remaining")
    val reset = response.header("X-RateLimit-Reset")
    val hint = if (response.code == 403 && remaining == "0") {
     " Лимит GitHub API исчерпан. Сброс Unix: " + reset + ". Добавьте PAT в настройках."
    } else if (response.code == 403) " Проверьте PAT и права доступа." else ""
    error("Ошибка API: HTTP " + response.code + " (" + url + ")." + hint)
   }
   return JSONObject(response.body?.string().orEmpty())
  }
 }

 private fun findApk(assets: JSONArray): String {
  for (i in 0 until assets.length()) {
   val asset = assets.optJSONObject(i) ?: continue
   val name = asset.optString("name")
   val candidate = asset.optString("browser_download_url")
   if (name.endsWith(".apk", true) || candidate.substringBefore('?').endsWith(".apk", true)) {
    if (candidate.startsWith("https://")) return candidate
   }
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
   put("error", a.error ?: JSONObject.NULL); put("packageName", a.packageName)
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
    o.optString("error").takeUnless { it.isBlank() || it == "null" }, o.optString("packageName"))
  }
 }.getOrDefault(emptyList())
}
