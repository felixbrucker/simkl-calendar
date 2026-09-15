plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.kover)
}

android {
  namespace = "com.felixbrucker.simklcalendar"
  compileSdk {
    version = release(37)
  }

  defaultConfig {
    applicationId = "com.felixbrucker.simklcalendar"
    minSdk = 26
    targetSdk = 37
    versionCode = 1
    versionName = "1.0.0"
    buildConfigField("String", "APP_NAME", "\"simkl-calendar\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
      storeFile = if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
        file(keystorePath)
      } else {
        file("${rootDir}/release.keystore")
      }
      storePassword = System.getenv("KEYSTORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS")
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    getByName("debug") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
      storeFile = if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
        file(keystorePath)
      } else {
        file("${rootDir}/debug.keystore")
      }
      storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
      keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
      keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
    aidl = true
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

kover {
  reports {
    total {
      verify {
        rule {
          minBound(90)
        }
      }
    }
    filters {
      excludes {
        classes(
          "*.BuildConfig",
          "*_*",
          "*JsonAdapter*",
          "com.felixbrucker.torrenthttpdownloader.*",
          "com.felixbrucker.simklcalendar.ui.composable.*",
          "com.felixbrucker.simklcalendar.ui.screens.*",
          "com.felixbrucker.simklcalendar.ui.theme.*",
          "com.felixbrucker.simklcalendar.MainActivity*",
        )
      }
    }
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  implementation(libs.torrent.search.api.kt)
  debugImplementation(libs.androidx.compose.ui.tooling)
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.kotlinx.coroutines.test)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
