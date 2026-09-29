plugins {
  id("com.android.application")
  kotlin("android")
  id("dev.flutter.flutter-gradle-plugin")
}

val appVersion = rootProject.file("../pubspec.yaml").useLines { lines ->
  lines.first { it.startsWith("version: ") }.substringAfter("version: ").trim().split("+")
}
require(appVersion.size == 2) { "pubspec.yaml must declare version: name+code" }
val signingPath = providers.environmentVariable("IFT_SIGNING_STORE_FILE").orNull

android {
  namespace = "dev.cedarflake.ift"
  compileSdk = 35

  defaultConfig {
    applicationId = "dev.cedarflake.infalsustouch"
    minSdk = 26
    targetSdk = 35
    versionCode = appVersion[1].toInt()
    versionName = appVersion[0]
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  testBuildType = providers.gradleProperty("iftTestBuildType").getOrElse("debug").also {
    require(it in setOf("debug", "profile"))
  }
  buildFeatures { buildConfig = true }
  if (signingPath != null) {
    signingConfigs {
      create("release") {
        storeFile = file(signingPath)
        storePassword = providers.environmentVariable("IFT_SIGNING_STORE_PASSWORD").get()
        keyAlias = providers.environmentVariable("IFT_SIGNING_KEY_ALIAS").get()
        keyPassword = providers.environmentVariable("IFT_SIGNING_KEY_PASSWORD").get()
      }
    }
    buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions.jvmTarget = "17"
  bundle {
    language { enableSplit = false }
  }
}

flutter {
  source = "../.."
}

dependencies {
  implementation(project(":touch"))
  implementation(project(":transport"))
  implementation(project(":settings"))
  implementation(project(":video"))
  androidTestImplementation("androidx.test:runner:1.6.2")
  androidTestImplementation("androidx.test:core:1.6.1")
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
