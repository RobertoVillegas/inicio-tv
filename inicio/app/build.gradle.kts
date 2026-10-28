import java.util.Properties

plugins {
    id("com.android.application")
}

// La llave de release vive fuera del repo. En la Mac: ~/.android/inicio-release.properties;
// en GitHub Actions el workflow la escribe desde los secrets y apunta INICIO_FIRMA a ella.
// Sin ese archivo (por ejemplo en un fork), se firma con la llave de debug.
val firma = (System.getenv("INICIO_FIRMA") ?: "${System.getProperty("user.home")}/.android/inicio-release.properties")
    .let { file(it) }
    .takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

android {
    namespace = "dev.robertovillegas.inicio"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.robertovillegas.inicio"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        if (firma != null) {
            create("release") {
                storeFile = file(firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
}
