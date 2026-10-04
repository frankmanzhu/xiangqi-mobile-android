plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.frankzhu.xiangqi"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.frankzhu.xiangqimobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Pikafish needs a 64-bit build; 32-bit ABIs are intentionally not shipped.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake { cppFlags += "-std=c++17" }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Release signing comes from the environment so no secret is ever committed:
    // XIANGQI_KEYSTORE (path), XIANGQI_KEYSTORE_PASSWORD, XIANGQI_KEY_ALIAS, XIANGQI_KEY_PASSWORD.
    val releaseKeystore = System.getenv("XIANGQI_KEYSTORE")
    if (releaseKeystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("XIANGQI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("XIANGQI_KEY_ALIAS")
                keyPassword = System.getenv("XIANGQI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time (used by the shared core module) needs desugaring below API 26.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlin { jvmToolchain(17) }

    buildFeatures { compose = true }

    // The network is opened as a raw file. The learning database is copied out once on first
    // use, so APK compression is fine and keeps the download under Play's size limit.
    androidResources { noCompress += listOf("nnue") }

    packaging {
        jniLibs { useLegacyPackaging = false }
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}


// --- Learning database ------------------------------------------------------------------------
// The shipped corpus is the CC BY 4.0 Chinese Chess Practical Dataset (58,456 records, ~90 MB),
// stored in this repository with Git LFS at app/learning/ccpd.sqlite3. Override the location with
// -Pxiangqi.learningDb=PATH or the XIANGQI_LEARNING_DB environment variable. If the LFS file has not
// been pulled (a Git LFS pointer is only ~130 bytes), builds fall back to the small subset in
// app/learning-lite - fine for CI and development, refused for release builds unless
// -Pxiangqi.allowLiteLearningDb=true is passed on purpose.
val learningDatabase: File? = (
    (findProperty("xiangqi.learningDb") as String?) ?: System.getenv("XIANGQI_LEARNING_DB")
        ?: "learning/ccpd.sqlite3"
).let { file(it) }.takeIf { it.isFile && it.length() > 1_000_000 }
val liteLearningDatabase = file("learning-lite/ccpd.sqlite3")
val forceLite = (findProperty("xiangqi.liteLearningDb") as String?) == "true"
val allowLite = (findProperty("xiangqi.allowLiteLearningDb") as String?) == "true"
val generatedLearningAssets = layout.buildDirectory.dir("generated/learningAssets")

val prepareLearningDatabase by tasks.registering {
    val source = if (forceLite || learningDatabase == null) liteLearningDatabase else learningDatabase
    val usingLite = source == liteLearningDatabase
    inputs.file(source)
    inputs.property("lite", usingLite)
    val target = generatedLearningAssets.map { it.file("learning/ccpd.sqlite3") }
    outputs.file(target)
    doLast {
        require(source.length() > 1_000_000) { "${source.path} is ${source.length()} bytes - not a database." }
        source.copyTo(target.get().asFile.also { it.parentFile.mkdirs() }, overwrite = true)
        logger.lifecycle("Learning database: ${if (usingLite) "LITE subset" else "full CCPD corpus"} (${source.length() / 1_000_000} MB) from ${source.path}")
    }
}

val releaseRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) || it.contains("bundle", ignoreCase = true) }
if (releaseRequested && (forceLite || learningDatabase == null) && !allowLite) {
    throw GradleException(
        "Release builds must bundle the full CCPD corpus, but app/learning/ccpd.sqlite3 is missing or is a Git LFS " +
            "pointer. Run `git lfs install && git lfs pull`, or pass -Pxiangqi.learningDb=PATH. " +
            "Pass -Pxiangqi.allowLiteLearningDb=true to ship the small subset deliberately."
    )
}

android.sourceSets.getByName("main").assets.srcDir(generatedLearningAssets)
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(prepareLearningDatabase)
    if (name.contains("lint", ignoreCase = true) || name.startsWith("generate") && name.endsWith("LintModel")) mustRunAfter(prepareLearningDatabase)
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
