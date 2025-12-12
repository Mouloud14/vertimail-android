plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.vertimailclient"
    compileSdk = 34 // Je conseille de mettre 34 (stable) au lieu de 36 (preview/bêta) pour éviter les bugs

    defaultConfig {
        applicationId = "com.example.vertimailclient"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // --- MODIFICATION IMPORTANTE ICI ---
    // On force la version 1.11.0 pour éviter le crash du Menu/FloatingButton
    // (Ne pas utiliser libs.material ici car il peut pointer vers une version bugguée)
    implementation("com.google.android.material:material:1.11.0")

    // Pour lire le JSON (facultatif si tu utilises org.json, mais utile)
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}