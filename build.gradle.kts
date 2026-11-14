plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("com.android.test") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.0.21" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.8.3" apply false
    id("androidx.baselineprofile") version "1.2.4" apply false
}

// Samakan dengan fork (Zehna/Lemuroid-Zehna): paksa semua artefak
// org.jetbrains.kotlin ke 2.0.21. Tanpa ini, padkit 1.0.0-beta1 (dependensi
// :lemuroid-touchinput) menarik kotlin-stdlib 2.1.21, dan Room 2.6.1 KAPT
// gagal membaca metadata 2.1.0 ("Provided Metadata instance has version
// 2.1.0, while maximum supported version is 2.0.0"). Fork lolos karena blok
// ini ada di root build-nya. JANGAN tambahkan blok repositories di sini —
// repositories sudah diatur di settings.gradle.kts (FAIL_ON_PROJECT_REPOS).
allprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            when (requested.group) {
                "com.google.android.gms" -> useVersion(deps.versions.gms)
                "org.jetbrains.kotlin" -> {
                    if (requested.name.startsWith("kotlin-stdlib-jre")) {
                        with(requested) {
                            useTarget("$group:${name.replace("jre", "jdk")}:$version")
                        }
                    }
                    useVersion(deps.versions.kotlin)
                }
            }
        }
    }
}

