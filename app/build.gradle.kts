plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lifenote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lifenote"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    val releasePassword = providers.environmentVariable("LIFENOTE_KEYSTORE_PASSWORD").orNull
    signingConfigs {
        if (file("keystore.jks").isFile && releasePassword != null) {
            create("lifenoteRelease") {
                storeFile = file("keystore.jks")
                storePassword = releasePassword
                keyAlias = "lifenote"
                keyPassword = releasePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("lifenoteRelease")?.let { signingConfig = it }
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
    // Intentionally empty — project law: zero third-party runtime dependencies.
}
