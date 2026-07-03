plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.glyphdialer.peripheral.glyph"
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

// The stroke library + choreography math are pure logic, unit-tested with JUnit5
// per CONVENTIONS.md §4/§11.
tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    // Allowed internal deps (CONVENTIONS.md §3): glyph may depend on :core:common,
    // and on :core:domain for the GlyphController interface + CallVisual enum.
    implementation(project(":core:common"))
    implementation(project(":core:domain"))

    // DI: Hilt module provides the GlyphController binding (Singleton).
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Async backbone for the debounced, non-blocking choreography dispatcher.
    implementation(libs.bundles.coroutines)

    // Logging (CONVENTIONS.md §5/§12): Timber, never android.util.Log.
    implementation(libs.timber)

    // NOTE(gdk): The Nothing GDK / Glyph Matrix AAR is NOT present at build time.
    // The controllers drive it ENTIRELY VIA REFLECTION so this module compiles
    // real hardware path, drop the official AAR into peripheral/glyph/libs/ and add
    // `implementation(files("libs/<gdk>.aar"))` here (see TODO in README/notes).
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))

    // Unit testing (JUnit5 + Truth + Turbine + coroutines-test + MockK).
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
