package dev.imranr.obtainium.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

private val iconHttp = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

/**
 * Finds a real Android launcher bitmap in a public GitHub repository.
 * Returns null for unsupported sources or repositories without a raster launcher icon.
 */
internal suspend fun findLauncherIcon(sourceUrl: String, token: String = ""): String? = withContext(Dispatchers.IO) {
    runCatching {
        val uri = URI(sourceUrl)
        if (uri.host !in setOf("github.com", "www.github.com")) return@runCatching null
        val parts = uri.path.trim('/').split('/')
        if (parts.size < 2) return@runCatching null
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".")
        fun apiRequest(url: String): Request {
            val builder = Request.Builder().url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Obtainium-Kotlin")
            if (token.isNotBlank()) builder.header("Authorization", "Bearer " + token)
            return builder.build()
        }
        val repository = iconHttp.newCall(
            apiRequest("https://api.github.com/repos/" + owner + "/" + repo)
        ).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            JSONObject(response.body?.string().orEmpty())
        }
        val defaultBranch = repository.optString("default_branch").ifBlank { "main" }
        val tree = iconHttp.newCall(
            apiRequest("https://api.github.com/repos/" + owner + "/" + repo + "/git/trees/" + defaultBranch + "?recursive=1")
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
            val raster = lower.endsWith(".png") || lower.endsWith(".webp")
            if (!raster || !lower.contains("/res/") && !lower.startsWith("res/")) continue
            if (!lower.contains("mipmap") && !lower.contains("drawable")) continue
            if (!lower.contains("ic_launcher") && !lower.contains("launcher")) continue
            candidates += path
        }
        val selected = candidates.minByOrNull { path ->
            val p = path.lowercase()
            when {
                p.contains("xxxhdpi") -> 0
                p.contains("xxhdpi") -> 1
                p.contains("xhdpi") -> 2
                p.contains("hdpi") -> 3
                p.contains("mdpi") -> 4
                p.contains("anydpi") -> 5
                else -> 6
            } * 10 + if (p.contains("round")) 1 else 0
        } ?: return@runCatching null
        "https://raw.githubusercontent.com/$owner/$repo/HEAD/$selected"
    }.getOrNull()
}
