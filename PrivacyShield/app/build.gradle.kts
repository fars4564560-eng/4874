plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.privacyshield"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.privacyshield"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("com.google.mlkit:face-detection:16.1.7")
    // libsu: يحتاج مستودع jitpack في settings.gradle
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
}
