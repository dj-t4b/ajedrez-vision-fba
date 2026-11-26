plugins {
    id("com.android.application")
}

android {
    namespace = "bo.utb.ajedrez.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "bo.utb.ajedrez.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Solo la arquitectura de los celulares actuales: el APK de OpenCV pesa mucho menos
        ndk { abiFilters += "arm64-v8a" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.camera:camera-camera2:1.7.0")
    implementation("androidx.camera:camera-lifecycle:1.7.0")
    implementation("androidx.camera:camera-view:1.7.0")
    implementation("org.opencv:opencv:4.14.0")
    implementation("com.github.bhlangonijr:chesslib:1.3.7")

    testImplementation("junit:junit:4.13.2")
}
