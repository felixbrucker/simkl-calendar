pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
    gradlePluginPortal {
      content {
        includeGroup("org.gradle.toolchains")
        includeGroup("org.gradle.toolchains.foojay-resolver-convention")
      }
    }
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
}

rootProject.name = "Simkl Calendar"

include(":app")
