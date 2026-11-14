import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.library")
    id("kotlin-android")
}

extensions.configure<LibraryExtension> {
    compileSdk = deps.android.compileSdkVersion
    namespace = "com.swordfish.lemuroid.cores"
    flavorDimensions += listOf("opensource", "cores")
    productFlavors {
        create("free") { dimension = "opensource" }
        create("bundle") { dimension = "cores" }
    }
}

dependencies {
    implementation(kotlin(deps.libs.kotlin.stdlib))
}
