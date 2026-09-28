pluginManagement {
  val flutterSdk = java.util.Properties().let { properties ->
    file("local.properties").inputStream().use(properties::load)
    requireNotNull(properties.getProperty("flutter.sdk")) { "Run scripts/build-android.ps1 to configure flutter.sdk" }
  }
  includeBuild("$flutterSdk/packages/flutter_tools/gradle")
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins {
  id("dev.flutter.flutter-plugin-loader") version "1.0.0"
  id("com.android.application") version "8.9.2" apply false
  id("com.android.library") version "8.9.2" apply false
  id("org.jetbrains.kotlin.android") version "2.1.20" apply false
  id("org.jetbrains.kotlin.jvm") version "2.1.20" apply false
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  repositories {
    google()
    mavenCentral()
    maven("https://storage.googleapis.com/download.flutter.io")
  }
}

rootProject.name = "InFalsusTouch"
include(":app", ":touch", ":transport", ":settings", ":video")
