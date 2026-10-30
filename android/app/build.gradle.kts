plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "dev.imranr.obtainium"
 compileSdk = 36
 defaultConfig {
  applicationId = "dev.imranr.obtainium"
  minSdk = 26
  targetSdk = 35
  versionCode = 100
  versionName = "1.0.0-kotlin"
 }
 buildTypes {
  release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
  debug { applicationIdSuffix = ".debug" }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
}
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
 debugImplementation("androidx.compose.ui:ui-tooling")
}
