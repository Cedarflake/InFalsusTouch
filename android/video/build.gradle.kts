plugins {
  id("com.android.library")
  kotlin("android")
}

android {
  namespace = "dev.cedarflake.ift.video"
  compileSdk = 35
  defaultConfig { minSdk = 26 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions.jvmTarget = "17"
}

dependencies {
  implementation(project(":transport"))
  testImplementation(kotlin("test-junit"))
}
