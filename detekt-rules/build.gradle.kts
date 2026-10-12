plugins {
  kotlin("jvm")
  java
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}

dependencies {
  compileOnly(libs.detekt.api)
  testImplementation(libs.detekt.api)
  testImplementation(libs.detekt.test)
  testImplementation(libs.junit)
}
