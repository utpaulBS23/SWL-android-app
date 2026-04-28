plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("com.google.devtools.ksp")
}

import java.util.concurrent.TimeUnit

android {
    namespace = "com.phq.swl.pioms"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phq.swl.pioms"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.1.0"
        manifestPlaceholders["MAPS_API_KEY"] = project.findProperty("MAPS_API_KEY")?.toString().orEmpty()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("../keystore.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEYSTORE_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val keystoreFile = file("../keystore.jks")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    applicationVariants.configureEach {
        kotlin.sourceSets {
            getByName(name) {
                kotlin.srcDir("build/generated/ksp/$name/kotlin")
            }
        }
    }
}

tasks.register("forceDeleteLockedRJar") {
    doLast {
        val rJar =
            layout.buildDirectory.file(
                "intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar",
            ).get().asFile
        if (!rJar.exists()) return@doLast

        val attempts = 8
        for (i in 1..attempts) {
            try {
                if (rJar.delete()) return@doLast
            } catch (_: Throwable) {
            }
            try {
                TimeUnit.MILLISECONDS.sleep((150L * i).coerceAtMost(1200L))
            } catch (_: InterruptedException) {
            }
        }
    }
}

tasks.matching { it.name == "processDebugResources" }.configureEach {
    dependsOn("forceDeleteLockedRJar")
}

ksp {
    arg("KOIN_CONFIG_CHECK", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.compose.material3.icons.extended)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.androidx.ui.text.google.fonts)

    // ObjectBox - vector database
    debugImplementation("io.objectbox:objectbox-android-objectbrowser:4.0.0")
    releaseImplementation("io.objectbox:objectbox-android:4.0.0")

    // dependency injection
    implementation(libs.koin.android)
    implementation(libs.koin.annotations)
    implementation(libs.koin.androidx.compose)
    ksp(libs.koin.ksp.compiler)

    // TensorFlow Lite dependencies
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.gpu.api)
    implementation(libs.tensorflow.lite.support)

    // DocumentFile and ExitInterface
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.exifinterface)

    // Kotlin Coil
    implementation(libs.coil)
    implementation(libs.coil.compose)

    // CameraX
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Google Maps + Location
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.maps.android:maps-compose:4.4.1")

    // Mediapipe Face Detection
    implementation(libs.tasks.vision)

    // MLKit Face Detection
    implementation("com.google.mlkit:face-detection:16.1.7")

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

apply(plugin = "io.objectbox")
