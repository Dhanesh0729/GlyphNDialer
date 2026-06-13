plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.glyphdialer.peripheral.transcription"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Explicitly include the Kotlin source roots per CONVENTIONS.md §2/§3.
    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
    }
}

// The segment-alignment / speaker-labeling logic is pure Kotlin and is unit-tested
// with JUnit5 per CONVENTIONS.md §4/§11.
tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    // Allowed internal deps (CONVENTIONS.md §3): transcription depends only on
    // :core:domain (TranscriptionEngine contract, Transcript entities) and
    // :core:common (AppResult, injected dispatchers).
    implementation(project(":core:domain"))
    implementation(project(":core:common"))

    // DI: Hilt provides the engine selector + the default TranscriptionEngine.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Async backbone for callbackFlow live captions + suspend file transcription.
    implementation(libs.bundles.coroutines)

    // Logging (CONVENTIONS.md §5/§12): Timber, never android.util.Log.
    implementation(libs.timber)

    // NOTE(whisper): The on-device Whisper runtime (whisper.cpp JNI .so OR a TFLite
    // model + interpreter) is NOT bundled at build time. WhisperEngine loads its
    // native library + model ENTIRELY VIA REFLECTION / a guarded loadLibrary so this
    // module compiles and runs without the binary, and lights up at runtime when the
    // model is dropped into assets/whisper/. See the TODO in WhisperEngine.kt.
    //
    // NOTE(mlkit/cloud): ML Kit STT and the cloud STT path are likewise NOT hard
    // dependencies — MlKitEngine reflection-guards the (currently non-existent) ML Kit
    // STT artifact, and CloudSttEngine documents the network boundary without pulling
    // a client. Both honestly report isAvailable == false until wired up (§2.4).

    // Unit testing (JUnit5 + Truth + Turbine + coroutines-test + MockK).
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
