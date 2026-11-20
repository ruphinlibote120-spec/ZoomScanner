plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zoomscanner.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zoomscanner.app"
        minSdk = 29
        targetSdk = 34
        // Chaque compilation GitHub augmente le numéro : la mise à jour s'installe par-dessus l'ancienne version.
        val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = run
        versionName = "1.0.$run"
        // Une seule bibliothèque native par processeur courant : APK plus léger.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        create("release") {
            // Clé fixe : indispensable pour que chaque mise à jour s'installe sans désinstaller.
            storeFile = file("zoomscanner.jks")
            storePassword = "zoomscanner"
            keyAlias = "zoomscanner"
            keyPassword = "zoomscanner"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    // Scanner de documents Google (détection des bords, recadrage, filtres, import galerie)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    // Lecteur de QR codes
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    // OCR (reconnaissance de texte, fonctionne hors ligne)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Manipulation de PDF (fusion, extraction, mot de passe, filigrane, signature)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Création de QR codes (hors ligne)
    implementation("com.google.zxing:core:3.5.3")
}
