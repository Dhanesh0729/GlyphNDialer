plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.glyphdialer.core.ui"
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

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }

    // Explicitly include the Kotlin source root per CONVENTIONS.md §2.
    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
        getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
    }
}

dependencies {
    // Theme tokens + shared utilities (CONVENTIONS.md §3 dependency graph).
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))

    // Jetpack Compose (BOM-pinned) + Material 3 — the rendering surface.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    // Lifecycle-aware Compose helpers (collectAsStateWithLifecycle, etc.).
    implementation(libs.bundles.lifecycle)

    // Async image loading for avatars/contact photos in list rows.
    implementation(libs.coil.compose)

    // Logging.
    implementation(libs.timber)

    // ui-tooling at runtime for @Preview rendering in the IDE.
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Pure-logic unit tests (JUnit5 + Truth) for the dependency-light helpers (§11).
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit.jupiter.engine)

    // Compose UI tests.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
