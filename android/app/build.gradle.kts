plugins {
  id("com.android.application")
  kotlin("android")
}

android {
  namespace = "dev.cedarflake.ift"
  compileSdk = 35

  defaultConfig {
    applicationId = "dev.cedarflake.infalsustouch"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "0.1.0-input"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions.jvmTarget = "17"
}

dependencies {
  implementation(project(":touch"))
  implementation(project(":transport"))
  implementation(project(":settings"))
  androidTestImplementation("androidx.test:runner:1.6.2")
  androidTestImplementation("androidx.test:core:1.6.1")
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
