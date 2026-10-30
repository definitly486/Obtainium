package dev.imranr.obtainium.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dev.imranr.obtainium.data.TrackedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class ApkInstaller(private val context: Context) {
 private val client = OkHttpClient.Builder()
  .connectTimeout(30, TimeUnit.SECONDS).readTimeout(2, TimeUnit.MINUTES).build()

 suspend fun downloadAndInstall(app: TrackedApp) = withContext(Dispatchers.IO) {
  require(app.apkUrl.isNotBlank()) { "Ссылка на APK пока не найдена. Сначала проверьте обновления." }
  val request = Request.Builder().url(app.apkUrl)
   .header("User-Agent", "Obtainium-Kotlin").build()
  val apk = File(context.cacheDir, "install-${app.id}.apk")
  client.newCall(request).execute().use { response ->
   if (!response.isSuccessful) error("Загрузка APK: HTTP ${response.code}")
   val body = response.body ?: error("Пустой ответ сервера")
   body.byteStream().use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
  }
  require(apk.length() > 0) { "Загруженный APK пуст" }

  val installer = context.packageManager.packageInstaller
  val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
  val sessionId = installer.createSession(params)
  try {
   installer.openSession(sessionId).use { session ->
    FileInputStream(apk).use { input ->
     session.openWrite("base.apk", 0, apk.length()).use { output ->
      input.copyTo(output)
      session.fsync(output)
     }
    }
    val callback = Intent(context, InstallResultReceiver::class.java)
    val pending = PendingIntent.getBroadcast(
     context, sessionId, callback,
     PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
    session.commit(pending.intentSender)
   }
  } catch (e: Exception) {
   runCatching { installer.abandonSession(sessionId) }
   throw e
  } finally {
   apk.delete()
  }
 }
}
