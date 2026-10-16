import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// Release signing credentials live in local.properties (gitignored, machine-local) rather than
// here, since this file is committed - see local.properties for where the keystore itself lives
// and why it must never be lost (every future update needs to be signed with the same key).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.mirror.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mirror.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // MediaPipe ships native (.so) libraries; only package the ABIs we can test on
        // real devices/emulators for MVP to keep APK size reasonable.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProperties.getProperty("MIRROR_RELEASE_STORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = localProperties.getProperty("MIRROR_RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("MIRROR_RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("MIRROR_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Falls back to no signing config (an unsigned, uninstallable build) if
            // local.properties doesn't have the release signing entries - e.g. CI or a
            // teammate's machine without the keystore - rather than failing the build outright.
            if (localProperties.getProperty("MIRROR_RELEASE_STORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    // The face_landmarker.task model file is a binary asset; don't let AAPT try to compress
    // or otherwise mangle it.
    androidResources {
        noCompress += "task"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // Needed for BuildConfig.VERSION_CODE, used by the in-app update check (see
        // ui/update/UpdateCheckViewModel.kt) - AGP 8+ disables BuildConfig generation by default.
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // On-device face landmark detection (Phase 1). Fully offline - no network calls.
    implementation(libs.mediapipe.tasks.vision)

    implementation(libs.coil.compose)

    implementation(libs.kotlinx.coroutines.android)

    // Custom Kotlin (Ktor) backend client - see ../backend
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // Push notifications (update alerts) - sent by mirrorcentral via the Firebase Admin SDK.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)

    // Google Sign-In - Credential Manager is Google's current recommended API, replacing the
    // older GoogleSignInClient/play-services-auth-only approach.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
