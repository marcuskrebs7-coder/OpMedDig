import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Play-udgavens upload-nøgle. Stien og adgangskoden ligger i keystore.properties
// (ikke i git). Mangler filen, kan Play-udgaven ikke bygges som release.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "dk.opmeddig"
    compileSdk = 36

    defaultConfig {
        applicationId = "dk.opmeddig"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.8"
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("upload") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    // To udgaver af samme app:
    //  github: hentes fra GitHub, opdaterer sig selv, signeret med den gamle debug-nøgle (så eksisterende installationer kan opdateres)
    //  play:   til Google Play, ingen selvopdatering, signeret med upload-nøglen
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "true")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // Signering vælges pr. udgave
    androidComponents {
        onVariants { variant ->
            if (variant.buildType == "release") {
                val name = if (variant.flavorName == "play") "upload" else "debug"
                signingConfigs.findByName(name)?.let { variant.signingConfig.setConfig(it) }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
}
