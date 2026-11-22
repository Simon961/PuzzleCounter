import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.puzzlecounter"
    compileSdk {
        // 37.1: the current Compose libraries are built against it (the target stays 37)
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.hugenay.puzzlecounter"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The upload key is not part of the project: keystore.properties (git-ignored, next to settings.gradle.kts) holds
    // storeFile, storePassword, keyAlias and keyPassword. Without it the release build is simply left unsigned.
    val keystoreFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use(::load) }
    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // R8 for everything (app, libraries): shrinks, optimizes and renames. The keep rules of the project are in
            // src/main/keepRules (the TensorFlow Lite classes that the Google Play services use by name).
            optimization {
                enable = true
            }
            // The app has no native code of its own, but its libraries bring some (TensorFlow Lite client, CameraX,
            // Compose graphics). This puts the symbol table of every native library that still has one into the
            // bundle, so that the Play Console can name the functions of a native crash. Today all of these libraries
            // are delivered stripped by their vendors (":app:extractReleaseNativeSymbolTables" says "has already been
            // stripped"), so there is nothing to upload yet and the Play Console hint stays; it is only a recommendation.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // The model is memory-mapped straight from the assets, which only works uncompressed.
        noCompress += "tflite"
        // The languages of the app (res/values-xx, res/resources.properties) for Android's per-app language setting.
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.exifinterface)
    // Per-app language (AppCompatDelegate.setApplicationLocales), also before Android 13
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // TensorFlow Lite runtime from Google Play services
    implementation(libs.play.services.tflite.java)
    implementation(libs.play.services.tflite.gpu)
    implementation(libs.play.services.tasks)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
