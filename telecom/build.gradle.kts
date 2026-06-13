plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.glyphdialer.telecom"
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

    // Match CONVENTIONS.md §2/§3: Kotlin sources live under src/main/kotlin.
    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
        getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Domain contracts (TelecomRepository, CallModel, GlyphController, WebRtcClient,
    // BlockedNumberRepository, AppResult-returning actions) + shared utilities.
    // NOTE (CONVENTIONS.md §3): we do NOT depend on :peripheral:glyph or
    // :peripheral:webrtc directly — GlyphController and WebRtcClient are injected as
    // domain interfaces, and the concrete impls are cross-wired in :app.
    implementation(project(":core:domain"))
    implementation(project(":core:common"))

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // LifecycleService base class for the bound telecom services.
    // (Catalog alias `androidx-lifecycle-service` -> libs.androidx.lifecycle.service.)
    implementation(libs.androidx.lifecycle.service)

    // AndroidX core: NotificationCompat / NotificationManagerCompat for the call
    // notification surface. (Catalog alias `androidx-core-ktx`.)
    implementation(libs.androidx.core.ktx)

    // Coroutines (StateFlow call registry, suspend actions) + Timber logging.
    implementation(libs.bundles.coroutines)
    implementation(libs.timber)

    // Unit tests (JUnit5 + Turbine + Truth + MockK + coroutines-test). Telecom state
    // transitions are unit-tested with Robolectric where framework types intrude
    // (CONVENTIONS.md §11/§23).
    testImplementation(libs.bundles.unit.test)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
