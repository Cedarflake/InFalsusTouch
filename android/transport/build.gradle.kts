plugins { kotlin("jvm") }

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
  compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies { testImplementation(kotlin("test-junit")) }

tasks.test {
  systemProperty("ift.protocolVectors", rootProject.file("../protocol/golden-vectors.txt").absolutePath)
  systemProperty("ift.testOutput", layout.buildDirectory.dir("interop").get().asFile.absolutePath)
}
