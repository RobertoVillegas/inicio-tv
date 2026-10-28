plugins {
    id("com.android.application")
}

android {
    namespace = "dev.robertovillegas.inicio"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.robertovillegas.inicio"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Firma con la llave de debug local: la app solo se instala en esta TV por ADB.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
