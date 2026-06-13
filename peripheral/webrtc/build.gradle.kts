plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.glyphdialer.peripheral.webrtc"
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
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    // §3 dependency graph: :peripheral:webrtc ──▶ :core:domain, :core:common, :telecom
    implementation(project(":telecom"))

    // WebRTC (org.webrtc via the maintained Stream fork).
    implementation(libs.stream.webrtc.android)

    // Ktor WebSocket signaling client + kotlinx.serialization message models.
    implementation(libs.bundles.ktor.client)
    implementation(libs.kotlinx.serialization.json)

    // CameraX (the WebRTC Camera2Capturer drives the camera directly, but CameraX
    // is on the dependency contract for enumeration/lens helpers and future use).
    implementation(libs.bundles.camerax)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines + logging
    implementation(libs.bundles.coroutines)
    implementation(libs.timber)

    // Unit tests (JUnit5 + Turbine + Truth + MockK + coroutines-test)
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
