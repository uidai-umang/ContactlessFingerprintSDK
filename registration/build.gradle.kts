import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "app.gov.uidai.registration"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        val localProperties = Properties().apply {
            rootProject.file("local.properties").inputStream().use { load(it) }
        }
        buildConfigField("String", "BACKEND_URL", "\"${localProperties.getProperty("BACKEND_URL")}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

configurations.all {
    resolutionStrategy {
        force("androidx.compose.foundation:foundation:1.7.6")   // matches compose-bom 2024.12.01
        force("androidx.compose.foundation:foundation-android:1.7.6")
    }
}

dependencies {
    implementation(project(":capture"))
    implementation(project(":core"))
    implementation(project(":utility"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.common)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.workmanager)
    implementation("dev.keiji.jp2:jp2-android:1.0.5")
    implementation(libs.commons.codec)
    implementation(libs.coil.compose)
    implementation(libs.gson)

    debugImplementation(libs.chucker.debug)
    releaseImplementation(libs.chucker.release)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.remote.config)
}