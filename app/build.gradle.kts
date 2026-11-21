import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystore = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

val patchedOpenhongguoAar = providers.gradleProperty("patchedOpenhongguoAar").orNull

android {
    namespace = "com.kadaliao.kaixinguo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kadaliao.kaixinguo"
        minSdk = 28
        targetSdk = 36
        versionCode = 20
        versionName = "0.4.15"
    }

    signingConfigs {
        if (keystore.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    lint {
        disable += "UnsafeOptInUsageError"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    if (patchedOpenhongguoAar != null) {
        implementation(files(patchedOpenhongguoAar))
    } else {
        implementation(libs.openhongguo) { artifact { type = "aar" } }
    }
    implementation(libs.coroutines.android)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.compose.icons)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.coroutines.test)
}
