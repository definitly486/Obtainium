package dev.imranr.obtainium.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private val iconHttp = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()

/**
 * Resolves a GitHub repository icon and stores the downloaded image in app-private
 * persistent storage. Returns the local file path so Compose/Coil does not depend
 * on a live raw.githubusercontent.com request every time the list is recomposed.
 */
internal suspend fun findLauncherIcon(
    context: Context,
    sourceUrl: String,
    token: String = ""
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val uri = URI(sourceUrl)
        if (uri.host !in setOf("github.com", "www.github.com")) return@runCatching null
        val parts = uri.path.trim('/').split('/')
        if (parts.size < 2) return@runCatching null
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".")
        val cacheDir = File(context.filesDir, "repository-icons").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${owner.lowercase()}/${repo.lowercase()}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cachedFile = File(cacheDir, "$digest.img")
        if (cachedFile.isFile && cachedFile.length() > 0L) return@runCatching cachedFile.absolutePath

        fun apiRequest(url: String): Request {
            val builder = Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Obtainium-Kotlin")
            if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
            return builder.build()
        }

        val repository = iconHttp.newCall(
            apiRequest("https://api.github.com/repos/$owner/$repo")
        ).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            JSONObject(response.body?.string().orEmpty())
        }
        val defaultBranch = repository.optString("default_branch").ifBlank { "main" }
        val tree = iconHttp.newCall(
            apiRequest("https://api.github.com/repos/$owner/$repo/git/trees/$defaultBranch?recursive=1")
        ).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            JSONObject(response.body?.string().orEmpty()).optJSONArray("tree")
        } ?: return@runCatching null

        val candidates = mutableListOf<String>()
        for (i in 0 until tree.length()) {
            val item = tree.optJSONObject(i) ?: continue
            if (item.optString("type") != "blob") continue
            val path = item.optString("path")
            val lower = path.lowercase()
            val raster = lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".jpg") || lower.endsWith(".jpeg")
            if (!raster) continue
            val androidLauncher = (lower.contains("/res/") || lower.startsWith("res/")) &&
                (lower.contains("mipmap") || lower.contains("drawable")) &&
                (lower.contains("ic_launcher") || lower.contains("launcher"))
            val fileName = lower.substringAfterLast("/")
            val metadataIcon = (fileName.startsWith("icon") || fileName.startsWith("logo") ||
                fileName.startsWith("app_icon") || fileName.startsWith("ic_launcher")) &&
                (lower.contains("fastlane/metadata") || lower.contains("/assets/") ||
                    lower.startsWith("assets/") || lower.contains("/images/"))
            if (androidLauncher || metadataIcon) candidates += path
        }

        val selected = candidates.minByOrNull { path ->
            val p = path.lowercase()
            val density = when {
                p.contains("xxxhdpi") -> 0
                p.contains("xxhdpi") -> 1
                p.contains("xhdpi") -> 2
                p.contains("hdpi") -> 3
                p.contains("mdpi") -> 4
                p.contains("anydpi") -> 5
                else -> 6
            }
            (if (p.contains("/res/") || p.startsWith("res/")) 0 else 100) +
                density * 10 + if (p.contains("round")) 1 else 0
        } ?: return@runCatching null

        val encodedPath = selected.split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val imageUrl = "https://raw.githubusercontent.com/$owner/$repo/$defaultBranch/$encodedPath"
        iconHttp.newCall(
            Request.Builder().url(imageUrl).header("User-Agent", "Obtainium-Kotlin").build()
        ).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val body = response.body ?: return@runCatching null
            val contentType = body.contentType()?.type
            if (contentType != "image") return@runCatching null
            if (body.contentLength() > 5L * 1024L * 1024L) return@runCatching null
            val bytes = body.bytes()
            if (bytes.isEmpty() || bytes.size > 5 * 1024 * 1024) return@runCatching null
            val tempFile = File(cacheDir, "$digest.tmp")
            tempFile.writeBytes(bytes)
            if (!tempFile.renameTo(cachedFile)) {
                tempFile.copyTo(cachedFile, overwrite = true)
                tempFile.delete()
            }
        }
        if (cachedFile.isFile && cachedFile.length() > 0L) cachedFile.absolutePath else null
    }.getOrNull()
}
