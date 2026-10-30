package dev.imranr.obtainium.data

data class TrackedApp(
 val id: String,
 val sourceUrl: String,
 val name: String,
 val latestVersion: String = "",
 val apkUrl: String = "",
 val lastChecked: Long = 0L,
 val error: String? = null
)
