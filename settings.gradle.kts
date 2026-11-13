pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "NexusDeck"
include(":app")
include(":retrograde-util", ":retrograde-app-shared",
        ":lemuroid-touchinput", ":lemuroid-metadata-libretro-db",
        ":lemuroid-app",
        ":lemuroid-app-ext-free", ":bundled-cores",
        ":baselineprofile")
project(":bundled-cores").projectDir = File("lemuroid-cores/bundled-cores")
