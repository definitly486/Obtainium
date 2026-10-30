package dev.imranr.obtainium.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

class InstallResultReceiver : BroadcastReceiver() {
 override fun onReceive(context: Context, intent: Intent) {
  when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
   PackageInstaller.STATUS_PENDING_USER_ACTION -> {
    val confirmation = if (Build.VERSION.SDK_INT >= 33) {
     intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
     @Suppress("DEPRECATION")
     intent.getParcelableExtra(Intent.EXTRA_INTENT)
    }
    confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (confirmation != null) context.startActivity(confirmation)
   }
   PackageInstaller.STATUS_SUCCESS ->
    Toast.makeText(context, "Приложение установлено", Toast.LENGTH_LONG).show()
   else -> {
    val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
    Toast.makeText(context, "Ошибка установки: ${detail ?: "неизвестная ошибка"}", Toast.LENGTH_LONG).show()
   }
  }
 }
}
