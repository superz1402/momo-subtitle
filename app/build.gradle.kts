plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.momosubtitle.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    // Persistent release signing: the keystore lives in-repo (keystore/) so the
    // same key survives server wipes — updates always install over previous
    // versions. Deliberate tradeoff, flagged to the operator (docs/RELEASE_CHECKLIST.md).
    val momoKeystoreProps = java.util.Properties().apply {
        val f = rootProject.file("keystore/momo.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

    signingConfigs {
        create("momo") {
            storeFile = rootProject.file(momoKeystoreProps.getProperty("storeFile"))
            storePassword = momoKeystoreProps.getProperty("storePassword")
            keyAlias = momoKeystoreProps.getProperty("keyAlias")
            keyPassword = momoKeystoreProps.getProperty("keyPassword")
        }
    }

    defaultConfig {
        applicationId = "com.momosubtitle.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 3
        versionName = "0.2.0"

        ndk {
            // Moto G85 5G (arm64-v8a) + x86_64 emulator.
            // armeabi-v7a joins in Phase 1 after FP16/NEON QA (docs/MODEL_STRATEGY.md).
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                // Release codegen even for debug APKs: unoptimized ggml is ~5-10x slower.
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release")
            }
        }
    }

    buildTypes {
        release {
            // R8 stays disabled for Milestone 0.1 (see docs/RELEASE_CHECKLIST.md before enabling).
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("momo")
        }
        debug {
            // Same key for debug builds so a debug install never conflicts with
            // the release signature — every update installs over the last one.
            signingConfig = signingConfigs.getByName("momo")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("../native/whisper/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    ndkVersion = libs.versions.ndk.get()

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:models"))
    implementation(project(":core:processing"))
    implementation(project(":core:storage"))
    implementation(project(":core:subtitles"))
    implementation(project(":core:ui"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:export"))
    implementation(project(":feature:home"))
    implementation(project(":feature:import"))
    implementation(project(":feature:models"))
    implementation(project(":feature:processing"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
