import com.android.build.api.variant.BuildConfigField
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.chaquopy)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.dagger.hilt)
    alias(libs.plugins.kotlin.ksp)
    id("maven-publish")
}

android {
    namespace = "app.gov.uidai.capture"
    compileSdk = 36

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1" // Use a version you have installed
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    publishing {
        singleVariant("release")
        singleVariant("debug")
    }
}

// BACKEND_URL comes from local.properties per build type (BACKEND_URL_DEBUG /
// BACKEND_URL_RELEASE). Resolved lazily so only the variant being built needs its key.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

androidComponents {
    onVariants { variant ->
        val key = "BACKEND_URL_${variant.buildType.orEmpty().uppercase()}"
        variant.buildConfigFields?.put(
            "BACKEND_URL",
            providers.provider {
                val url = localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
                    ?: throw GradleException(
                        "Missing $key in local.properties (needed for the ${variant.name} BACKEND_URL buildConfigField)."
                    )
                BuildConfigField("String", "\"$url\"", null)
            }
        )
    }
}

configurations.all {
    resolutionStrategy {
        force("androidx.compose.foundation:foundation:1.7.6")   // matches compose-bom 2024.12.01
        force("androidx.compose.foundation:foundation-android:1.7.6")
    }
}

chaquopy {
    defaultConfig {
        pip {
            install("numpy")
            install("pillow")
            install("opencv-python-headless")
        }
    }
}

dependencies {

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    // Camera 2
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Dagger Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // TFLite
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.gpu.api)

    // Jackson
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.xml)
    implementation(libs.stax.api)

    // Preference
    implementation(libs.androidx.preference.ktx)

    // MediaPipe
    implementation(libs.tasks.vision)

    // Material 3
    implementation(libs.androidx.material3)

    // Gson
    implementation(libs.gson)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.hilt)

    // Registration flow
    implementation(libs.androidx.hilt.common)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.workmanager)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit / OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation("dev.keiji.jp2:jp2-android:1.0.5")
    implementation(libs.commons.codec)
    implementation(libs.coil.compose)

    // Chucker
    debugImplementation(libs.chucker.debug)
    releaseImplementation(libs.chucker.release)

    implementation(libs.firebase.remote.config)
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                artifactId = "capture"
            }
            register<MavenPublication>("debug") {
                from(components["debug"])
                artifactId = "capture"
            }
        }
    }
}