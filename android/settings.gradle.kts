pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
plugins {
 id("com.android.application") version "9.2.1" apply false
 id("org.jetbrains.kotlin.android") version "2.4.10" apply false
 id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
dependencyResolutionManagement {
 repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
 repositories { google(); mavenCentral() }
}
rootProject.name = "Obtainium"
include(":app")
