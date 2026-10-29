import java.util.Properties

plugins {
    id("com.android.application")
}

// The release key lives outside the repo: ~/.android/inicio-release.properties on the Mac; in GitHub
// Actions the workflow writes it from secrets and points INICIO_SIGNING at it. Without it (a fork, say),
// the build is signed with the debug key.
val signing = (System.getenv("INICIO_SIGNING") ?: "${System.getProperty("user.home")}/.android/inicio-release.properties")
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
        versionCode = 7
        versionName = "0.5.1"
    }

    signingConfigs {
        if (signing != null) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
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
