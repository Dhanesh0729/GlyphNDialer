plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.glyphdialer.peripheral.recording"
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

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Async backbone
    implementation(libs.bundles.coroutines)

    // androidx.core for NotificationCompat / ServiceCompat / ContextCompat.
    implementation(libs.androidx.core.ktx)

    // Media3 — optional (per module spec), available for opportunistic level metering /
    // future in-module playback verification. Current metering uses a lightweight PCM
    // peak detector (see PcmUtils), so this is not on the hot path.
    implementation(libs.media3.exoplayer)

    // Logging (CONVENTIONS.md §5/§12): Timber, never android.util.Log.
    implementation(libs.timber)

    // Unit tests (JUnit5 + Turbine + Truth + MockK + coroutines-test).
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
