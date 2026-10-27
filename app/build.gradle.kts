import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---- Firebase config (optional) -------------------------------------------------
// Read from env GOOGLE_SERVICES_JSON (GitHub secret) or app/google-services.json.
// Only the client registered for com.bahikhata.app is used. Without it the app runs offline.
val appPackage = "com.bahikhata.app"
val gsText: String? = System.getenv("GOOGLE_SERVICES_JSON")?.takeIf { it.isNotBlank() }
    ?: file("google-services.json").takeIf { it.exists() }?.readText()
var fbApiKey = ""
var fbAppId = ""
var fbProjectId = ""
var fbStorage = ""
if (gsText != null) {
    try {
        val root = JsonSlurper().parseText(gsText) as Map<*, *>
        val info = root["project_info"] as Map<*, *>
        val clients = root["client"] as List<*>
        val mine = clients.map { it as Map<*, *> }.firstOrNull {
            val ci = it["client_info"] as Map<*, *>
            val aci = ci["android_client_info"] as Map<*, *>
            aci["package_name"] == appPackage
        }
        if (mine == null) {
            logger.warn("google-services.json has no client for $appPackage - cloud login disabled")
        } else {
            fbProjectId = info["project_id"]?.toString() ?: ""
            fbStorage = info["storage_bucket"]?.toString() ?: ""
            fbAppId = (mine["client_info"] as Map<*, *>)["mobilesdk_app_id"]?.toString() ?: ""
            fbApiKey = ((mine["api_key"] as List<*>).first() as Map<*, *>)["current_key"]?.toString() ?: ""
            logger.lifecycle("Firebase config found for project $fbProjectId")
        }
    } catch (e: Exception) {
        logger.warn("Could not read google-services.json: ${e.message}")
    }
} else {
    logger.lifecycle("No google-services.json - building offline-only (cloud login setup pending)")
}

android {
    namespace = "com.bahikhata.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bahikhata.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("VERSION_CODE") ?: "0")
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "FB_API_KEY", "\"$fbApiKey\"")
        buildConfigField("String", "FB_APP_ID", "\"$fbAppId\"")
        buildConfigField("String", "FB_PROJECT_ID", "\"$fbProjectId\"")
        buildConfigField("String", "FB_STORAGE", "\"$fbStorage\"")
    }

    // Release signing: CI writes app/release.keystore from a repo secret.
    // Bina keystore ke (jaise Android Studio me) release build debug key se sign hota hai.
    val releaseKeystore = file("release.keystore")
    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("KEY_ALIAS") ?: "bahikhata"
            keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        release {
            signingConfig = if (releaseKeystore.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
