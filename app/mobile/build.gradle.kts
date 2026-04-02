import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.oss.licenses)
}

val localPropsFile = rootProject.file("local.properties")
val localProps = if (localPropsFile.exists()) {
    Properties().apply { load(localPropsFile.inputStream()) }
} else {
    Properties()
}

android {
    namespace = "com.moodfox"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moodfox"
        minSdk = 28
        targetSdk = 36
        versionCode = 6
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val ksPath = localProps.getProperty("KEYSTORE_PATH") ?: ""
            if (ksPath.isNotEmpty()) {
                storeFile = file(ksPath)
                storePassword = localProps.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = localProps.getProperty("KEY_ALIAS", "")
                keyPassword = localProps.getProperty("KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.runtime)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.work.runtime)
    implementation(libs.work.hilt)
    ksp(libs.work.hilt.compiler)

    // Ktor HTTP client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // DataStore
    implementation(libs.datastore.preferences)

    // Drag-to-reorder
    implementation(libs.reorderable)

    // OSS Licenses
    implementation(libs.play.services.oss.licenses)

    // Excel export
    implementation(libs.fastexcel)
}
