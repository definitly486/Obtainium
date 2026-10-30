import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
val signingProperties = Properties()
val signingFile = rootProject.file("key.properties")
if (signingFile.exists()) signingFile.inputStream().use { signingProperties.load(it) }

android {
 namespace = "dev.imranr.obtainium"
 compileSdk = 36
 defaultConfig {
  applicationId = "dev.imranr.obtainium"
  minSdk = 26
  targetSdk = 35
  versionCode = 100
  versionName = "1.0.0-kotlin"
  ndk { abiFilters += "armeabi-v7a" }
 }
 signingConfigs {
  create("release") {
   if (signingFile.exists()) {
    keyAlias = signingProperties.getProperty("keyAlias")
    keyPassword = signingProperties.getProperty("keyPassword")
    storePassword = signingProperties.getProperty("storePassword")
    storeFile = file(signingProperties.getProperty("storeFile"))
   }
  }
 }
 buildTypes {
  release {
   if (signingFile.exists()) signingConfig = signingConfigs.getByName("release")
   isMinifyEnabled = true
   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
  }
  debug { applicationIdSuffix = ".debug" }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildFeatures { compose = true }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
 val bom = platform("androidx.compose:compose-bom:2026.06.00")
 implementation(bom)
 implementation("androidx.activity:activity-compose:1.12.2")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
 implementation("androidx.datastore:datastore-preferences:1.2.1")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 implementation("com.squareup.okhttp3:okhttp:5.3.2")
 implementation("io.coil-kt:coil-compose:2.7.0")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
