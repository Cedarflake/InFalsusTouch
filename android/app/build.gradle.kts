plugins {
  id("com.android.application")
  kotlin("android")
  id("dev.flutter.flutter-gradle-plugin")
}

android {
  namespace = "dev.cedarflake.ift"
  compileSdk = 35

  defaultConfig {
    applicationId = "dev.cedarflake.infalsustouch"
    minSdk = 26
    targetSdk = 35
    versionCode = 5
    versionName = "0.5.0-prototype"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  testBuildType = providers.gradleProperty("iftTestBuildType").getOrElse("debug").also {
    require(it in setOf("debug", "profile"))
  }
  buildFeatures { buildConfig = true }
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
