plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val bundledNdkVersion = "27.2.12479018"
val generatedCppRuntimeDir = layout.buildDirectory.dir("generated/ffmpegCppRuntime/jniLibs")
val generatedRustLibraryRoot = layout.buildDirectory.dir("generated/rustNative")
val rustTargetBuildDir = layout.buildDirectory.dir("rustTarget")

android {
    namespace = "com.ridthewann.ubahvo"
    compileSdk = 36
    ndkVersion = bundledNdkVersion

    defaultConfig {
        applicationId = "com.ridthewann.ubahvo"
        minSdk = 29
        targetSdk = 36
        versionCode = 16
        versionName = "0.1.0-beta.16"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Keep only the locales used by the current Indonesian UI and English fallback.
        resourceConfigurations += setOf("en", "id")
        // Keep direct-download support for ARM phones and add x86_64 for Android emulators/Intel devices.
        // ABI-specific outputs keep native downloads smaller; the universal APK remains available.
        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DUBAHVO_RUST_LIBRARY_ROOT=${generatedRustLibraryRoot.get().asFile.absolutePath}"
                )
            }
        }
    }

    // Produce small per-ABI APKs and retain one universal APK for users who prefer one download.
    splits {
        abi {
            // AGP cannot shrink split APK resources and bundle an AAB in the same variant task graph.
            // CI uses -Pubahvo.bundleOnly=true for its separate App Bundle build invocation.
            isEnable = !providers.gradleProperty("ubahvo.bundleOnly").isPresent
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
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
            // Shrink Kotlin/Java code and unused Android resources in downloadable beta APKs.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("release") {
            // Prepare an optimized App Bundle candidate. CI intentionally leaves it unsigned
            // unless a dedicated release-signing configuration is supplied outside the repo.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
    description = "Stages ABI-matched libc++ runtimes from the pinned Android NDK into generated JNI libraries."
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
            "armeabi-v7a" to "arm-linux-androideabi",
            "x86_64" to "x86_64-linux-android"
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


val prepareRustNativeCore = tasks.register("prepareRustNativeCore") {
    group = "build setup"
    description = "Cross-compiles the Rust URL preflight core for ARM phones and x86_64 emulators."
    inputs.files(fileTree("src/main/rust"))
    outputs.dir(generatedRustLibraryRoot)
    outputs.dir(rustTargetBuildDir)

    doLast {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()
        val isWindows = osName.contains("windows")
        val hostTag = when {
            isWindows -> "windows-x86_64"
            osName.contains("mac") || osName.contains("darwin") ->
                if (osArch.contains("arm64") || osArch.contains("aarch64")) "darwin-arm64" else "darwin-x86_64"
            osName.contains("linux") ->
                if (osArch.contains("arm64") || osArch.contains("aarch64")) "linux-aarch64" else "linux-x86_64"
            else -> throw GradleException("Unsupported host OS for Rust Android cross-compilation: " + osName)
        }

        val ndkRoot = File(android.sdkDirectory, "ndk/" + bundledNdkVersion)
        val linkerDirectory = File(ndkRoot, "toolchains/llvm/prebuilt/" + hostTag + "/bin")
        if (!linkerDirectory.isDirectory) {
            throw GradleException("Android NDK linker directory is missing: " + linkerDirectory.absolutePath)
        }

        data class RustTarget(val abi: String, val triple: String, val linker: String)
        val targets = listOf(
            RustTarget("arm64-v8a", "aarch64-linux-android", "aarch64-linux-android29-clang"),
            RustTarget("armeabi-v7a", "armv7-linux-androideabi", "armv7a-linux-androideabi29-clang"),
            RustTarget("x86_64", "x86_64-linux-android", "x86_64-linux-android29-clang")
        )
        val rustManifest = file("src/main/rust/Cargo.toml")
        val cargoExecutable = System.getenv("CARGO") ?: if (isWindows) "cargo.exe" else "cargo"
        val generatedRoot = generatedRustLibraryRoot.get().asFile
        generatedRoot.deleteRecursively()

        targets.forEach { target ->
            val linkerName = target.linker + if (isWindows) ".cmd" else ""
            val linker = File(linkerDirectory, linkerName)
            if (!linker.isFile) {
                throw GradleException(
                    "Android NDK linker for ${target.abi} is missing: " + linker.absolutePath
                )
            }

            val linkerEnvironment = "CARGO_TARGET_" +
                target.triple.replace('-', '_').uppercase() + "_LINKER"
            project.exec {
                workingDir(rootProject.projectDir)
                executable = cargoExecutable
                args(
                    "build",
                    "--release",
                    "--locked",
                    "--manifest-path",
                    rustManifest.absolutePath,
                    "--target",
                    target.triple
                )
                environment(linkerEnvironment, linker.absolutePath)
                environment("CARGO_TARGET_DIR", rustTargetBuildDir.get().asFile.absolutePath)
            }

            val archive = File(
                rustTargetBuildDir.get().asFile,
                target.triple + "/release/libubahvo_native_core.a"
            )
            if (!archive.isFile || archive.length() <= 0L) {
                throw GradleException("Rust failed to produce the native core archive for ${target.abi}.")
            }

            val destination = File(generatedRoot, target.abi + "/libubahvo_native_core.a")
            if (!destination.parentFile.mkdirs() && !destination.parentFile.isDirectory) {
                throw GradleException("Could not create Rust archive directory for ${target.abi}.")
            }
            archive.copyTo(destination, overwrite = true)
        }
    }
}

// Native configuration needs the Rust static archives before CMake evaluates imported targets.
tasks.configureEach {
    if (name.startsWith("configureCMake")) {
        dependsOn(prepareRustNativeCore)
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareNdkCppRuntime, prepareRustNativeCore)
}

// Give both the one-file universal APK and each lean ABI-specific APK stable product names.
val renameUbahvoDebugApks = tasks.register("renameUbahvoDebugApks") {
    group = "build"
    description = "Renames the universal and ABI-specific debug APKs to stable UBAHVO product names."

    doLast {
        val apkDirectory = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        val appVersion = android.defaultConfig.versionName
            ?: throw GradleException("Could not read the UBAHVO versionName.")

        val apkNames = linkedMapOf(
            "universal" to "app-universal-debug.apk",
            "arm64-v8a" to "app-arm64-v8a-debug.apk",
            "armeabi-v7a" to "app-armeabi-v7a-debug.apk",
            "x86_64" to "app-x86_64-debug.apk"
        )

        apkNames.forEach { (label, sourceName) ->
            val source = File(apkDirectory, sourceName)
            val destination = File(apkDirectory, "UBAHVO-" + appVersion + "-" + label + "-debug.apk")
            if (!source.isFile || source.length() <= 0L) {
                throw GradleException("Missing or empty " + label + " APK before branding rename: " + source.absolutePath)
            }
            if (destination.exists() && !destination.delete()) {
                throw GradleException("Could not replace previous UBAHVO APK: " + destination.absolutePath)
            }
            if (!source.renameTo(destination)) {
                throw GradleException("Could not rename APK to UBAHVO filename: " + destination.absolutePath)
            }
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

    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Maintained HTTP transport with a custom DNS hook for SSRF-safe public image fetching.
    implementation("com.squareup.okhttp3:okhttp:5.4.0")

    // Keep Jackson modules aligned and current; updater code parses untrusted remote release metadata.
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.22.4"))
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // yt-dlp runtime + FFmpeg binaries. Review all upstream licenses before redistribution.
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    testImplementation("junit:junit:4.13.2")
}
