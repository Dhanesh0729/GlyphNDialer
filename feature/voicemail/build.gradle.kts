// SPDX-License-Identifier: Apache-2.0
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.glyphdialer.feature.voicemail"
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

    buildFeatures {
        compose = true
    }

    // Kotlin sources live under src/main/kotlin (CONVENTIONS.md §2). AGP's default
    // source set is src/main/java; add the kotlin dir explicitly to be safe.
    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
    }
}

dependencies {
    // Feature → core only (CONVENTIONS.md §3). No cross-feature, no :telecom dep:
    // "call back" and "dial carrier voicemail" are surfaced as one-shot effects the
    // :app host wires to TelecomManager / Intents.
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.activity.compose) // BackHandler
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Media3 (ExoPlayer) for visual-voicemail audio playback + scrubbing (§8).
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)

    // Logging
    implementation(libs.timber)

    // Unit tests (JUnit5 + Turbine + Truth + MockK; CONVENTIONS.md §11).
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit.jupiter.engine)

    // Compose UI tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
