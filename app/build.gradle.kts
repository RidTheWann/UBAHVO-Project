plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val bundledNdkVersion = "27.2.12479018"
val generatedCppRuntimeDir = layout.buildDirectory.dir("generated/ffmpegCppRuntime/jniLibs")

android {
    namespace = "com.ridthewann.ubahvo"
    compileSdk = 36
    ndkVersion = bundledNdkVersion

    defaultConfig {
        applicationId = "com.ridthewann.ubahvo"
        minSdk = 29
        targetSdk = 36
        versionCode = 8
        versionName = "0.1.0-beta.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // One direct-download APK supports 64-bit and 32-bit ARM Android phones.
        // x86/x86_64 are omitted to keep the universal APK smaller.
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
    }

    sourceSets {
        getByName("main") {
            // Adds the ABI-matched NDK C++ runtime used to repair the upstream FFmpeg payload at startup.
            jniLibs.srcDir(generatedCppRuntimeDir)
        }
    }

    buildTypes {
        getByName("debug") {
            // Shrink Kotlin/Java code and unused Android resources in the downloadable beta APK.
            // Native media runtimes remain the main size cost of a universal APK.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            // Keep native runtimes extracted so the packaged Python/FFmpeg tools can execute.
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

val prepareNdkCppRuntime = tasks.register("prepareNdkCppRuntime") {
    group = "build setup"
    description = "Stages ARM-only libc++ runtimes from the pinned Android NDK into generated JNI libraries."
    outputs.dir(generatedCppRuntimeDir)
    outputs.upToDateWhen { false }

    doLast {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()
        val hostTag = when {
            osName.contains("windows") -> "windows-x86_64"
            osName.contains("mac") || osName.contains("darwin") ->
                if (osArch.contains("arm64") || osArch.contains("aarch64")) "darwin-arm64" else "darwin-x86_64"
            osName.contains("linux") ->
                if (osArch.contains("arm64") || osArch.contains("aarch64")) "linux-aarch64" else "linux-x86_64"
            else -> throw GradleException("Unsupported host OS for the pinned Android NDK: " + osName)
        }

        val ndkRoot = File(android.sdkDirectory, "ndk/" + bundledNdkVersion)
        if (!ndkRoot.isDirectory) {
            throw GradleException("Android NDK " + bundledNdkVersion + " is missing. Install it using SDK Manager.")
        }

        val sysrootLibs = File(
            ndkRoot,
            "toolchains/llvm/prebuilt/" + hostTag + "/sysroot/usr/lib"
        )
        val abiTriples = linkedMapOf(
            "arm64-v8a" to "aarch64-linux-android",
            "armeabi-v7a" to "arm-linux-androideabi"
        )
        val generatedRoot = generatedCppRuntimeDir.get().asFile
        generatedRoot.deleteRecursively()

        abiTriples.forEach { (abi, triple) ->
            val source = File(File(sysrootLibs, triple), "libc++_shared.so")
            if (!source.isFile || source.length() <= 0L) {
                throw GradleException(
                    "Missing libc++_shared.so for ABI " + abi + " in pinned NDK " + bundledNdkVersion
                )
            }
            val destination = File(File(generatedRoot, abi), "libc++_shared.so")
            if (!destination.parentFile.mkdirs() && !destination.parentFile.isDirectory) {
                throw GradleException("Could not create generated native library directory for ABI " + abi)
            }
            source.copyTo(destination, overwrite = true)
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareNdkCppRuntime)
}

// Android Gradle Plugin's default APK names expose the generic "app" module name.
// Rename the single universal ARM debug APK to the public product name after every assembleDebug.
val renameUbahvoDebugApks = tasks.register("renameUbahvoDebugApks") {
    group = "build"
    description = "Renames the single universal ARM debug APK to the UBAHVO product name."

    doLast {
        val apkDirectory = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        val appVersion = android.defaultConfig.versionName
            ?: throw GradleException("Could not read the UBAHVO versionName.")

        val source = File(apkDirectory, "app-debug.apk")
        val destination = File(apkDirectory, "UBAHVO-" + appVersion + "-universal-debug.apk")

        if (!source.isFile || source.length() <= 0L) {
            throw GradleException("Missing or empty universal APK before branding rename: " + source.absolutePath)
        }
        if (destination.exists() && !destination.delete()) {
            throw GradleException("Could not replace previous universal APK: " + destination.absolutePath)
        }
        if (!source.renameTo(destination)) {
            throw GradleException("Could not rename APK to UBAHVO universal filename: " + destination.absolutePath)
        }
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(renameUbahvoDebugApks)
}

dependencies {
    // Compose 1.9.x is compatible with the pinned AGP 8.13 / Android API 36 toolchain.
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Keep Jackson modules aligned and current; updater code parses untrusted remote release metadata.
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.22.4"))
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // yt-dlp runtime + FFmpeg binaries. Review all upstream licenses before redistribution.
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
