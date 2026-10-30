package dev.imranr.obtainium.install

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.app.PendingIntent
import android.os.Build
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
  .connectTimeout(30, TimeUnit.SECONDS)
  .readTimeout(3, TimeUnit.MINUTES)
  .callTimeout(5, TimeUnit.MINUTES)
  .followRedirects(true)
  .build()

 suspend fun downloadAndInstall(app: TrackedApp, githubToken: String = "", onProgress: (Long, Long) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
  require(app.apkUrl.isNotBlank()) {
   "APK не найден. Нажмите «Проверить» и убедитесь, что в выбранном релизе есть APK-файл."
  }

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
   !context.packageManager.canRequestPackageInstalls()) {
   val settingsIntent = Intent(
    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
    Uri.parse("package:${context.packageName}")
   ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   context.startActivity(settingsIntent)
   error("Разрешите установку неизвестных приложений для Obtainium, затем снова нажмите «Скачать и установить».")
  }

  val requestBuilder = Request.Builder().url(app.apkUrl)
   .header("User-Agent", "Obtainium-Kotlin")
   .header("Accept", "application/octet-stream")
  if (githubToken.isNotBlank() &&
   Uri.parse(app.apkUrl).host in setOf("github.com", "api.github.com")) {
   requestBuilder.header("Authorization", "Bearer $githubToken")
  }

  val apk = File(context.cacheDir, "install-${app.id}.apk")
  try {
   client.newCall(requestBuilder.build()).execute().use { response ->
    if (!response.isSuccessful) error("Загрузка APK: HTTP ${response.code}")
    val body = response.body ?: error("Сервер вернул пустой ответ вместо APK")
    if (body.contentLength() > 0 && body.contentLength() > 1_000_000_000L) {
     error("Размер APK превышает допустимый предел")
    }
    val total = body.contentLength()
    body.byteStream().use { input ->
     apk.outputStream().buffered().use { output ->
      val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
      var downloaded = 0L
      var read: Int
      onProgress(0L, total)
      while (input.read(buffer).also { read = it } != -1) {
       output.write(buffer, 0, read)
       downloaded += read
       onProgress(downloaded, total)
      }
      output.flush()
     }
    }
   }
   require(apk.length() > 2) { "Скачанный файл пуст или повреждён" }
   FileInputStream(apk).use { input ->
    val signature = ByteArray(2)
    require(input.read(signature) == 2 && signature[0] == 0x50.toByte() &&
     signature[1] == 0x4b.toByte()) {
     "Сервер вернул не APK-файл (возможно, страницу ошибки или HTML)"
    }
   }

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
   }
  } finally {
   apk.delete()
  }
 }
}
