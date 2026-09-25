import java.util.Properties

plugins { alias(libs.plugins.android.application) }

android {
    namespace = "com.iftl.threedee.viewer"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.iftl.threedee.viewer"
        minSdk = 24
        targetSdk = 37
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Low-end target devices are ARM; skip x86 emulator ABIs to keep the APK small.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    val releaseCredentials =
        Properties().apply {
            val file = rootProject.file(".signing/release.properties")
            if (file.exists()) file.inputStream().use { load(it) }
        }
    signingConfigs {
        if (releaseCredentials.isNotEmpty())
            create("submission") {
                storeFile = rootProject.file(releaseCredentials.getProperty("storeFile"))
                storePassword = releaseCredentials.getProperty("storePassword")
                keyAlias = releaseCredentials.getProperty("keyAlias")
                keyPassword = releaseCredentials.getProperty("keyPassword")
            }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("submission")
            optimization { enable = true }
        }
        debug {
            // Dev-loop convenience: x86_64 emulators have no ARM translation on this box.
            // The release build stays ARM-only for the actual low-end target device.
            ndk { abiFilters += "x86_64" }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        // Filament 1.77.0's Kotlin artifacts (filament-utils-android) were built with a newer
        // Kotlin than AGP 9.4.1's bundled built-in compiler ships. The metadata check is purely
        // advisory here -- the JVM bytecode itself is unaffected -- so skip it rather than pin
        // the whole project to an older Filament release.
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio.android)
    implementation(libs.filament.utils.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
