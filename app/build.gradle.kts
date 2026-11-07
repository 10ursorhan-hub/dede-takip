plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dedetakip.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dedetakip.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }

    // Sabit imza: CI, gizli KEYSTORE_B64 secret'ından app/dede.keystore dosyasını yazar.
    // Dosya varsa onunla imzala → her güncelleme aynı imza, üstüne kurulur (kaldırmak gerekmez).
    val ksFile = file("dede.keystore")
    if (ksFile.exists()) {
        signingConfigs {
            create("ortak") {
                storeFile = ksFile
                storePassword = System.getenv("KS_PASS") ?: ""
                keyAlias = "dede"
                keyPassword = System.getenv("KS_PASS") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            if (ksFile.exists()) signingConfig = signingConfigs.getByName("ortak")
        }
        release {
            isMinifyEnabled = false
            if (ksFile.exists()) signingConfig = signingConfigs.getByName("ortak")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // İstek üzerine foto/video için CameraX + servis içinde yaşam döngüsü
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-video:1.3.4")
}
