plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.k2fsa.sherpa.onnx.tts.engine"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.woheller69.ttsengine"
        minSdk = 29
        targetSdk = 35
        versionCode = 33
        versionName = "3.3"

        vectorDrawables {
            useSupportLibrary = true
        }

        buildFeatures {
            viewBinding = true
            buildConfig = true
            compose = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
        }
    }

    java {
        toolchain {
            // JDK 17 matches the CI runner and modern AGP; keeps toolchain
            // provisioning trivial (no JDK 11 needed on the build machine).
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Both sherpa-onnx AND onnxruntime-android bundle libonnxruntime.so, which would
        // otherwise fail the build with a duplicate-native-library error. Keep the first one.
        // NOTE: pickFirst can leave the ORT Java API (from onnxruntime-android) and the actually
        // packaged .so at different versions -> a device-verification risk for the phonikud path.
        jniLibs {
            pickFirsts += "**/libonnxruntime.so"
        }
    }
    lint {
        disable += "MissingTranslation"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.0")
    // onnxruntime-android drives the phonikud premium Hebrew models (diacritizer + Piper VITS).
    // 1.20.0 is a widely-available stable release. See packaging{} pickFirsts for the shared .so.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("com.github.woheller69:FreeDroidWarn:+")
    implementation("org.jsoup:jsoup:1.22.1")
    implementation ("androidx.work:work-runtime:2.10.2")

    testImplementation("junit:junit:4.13.2")
}