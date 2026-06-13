plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.glyphdialer.core.designsystem"
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

    // Explicitly include the Kotlin source root per CONVENTIONS.md §2.
    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
    }
}

dependencies {
    // Settings enums (ThemeMode / AppFont / AccentColor) live in :core:domain per
    // CONVENTIONS.md §6. The design system maps them onto Compose theme tokens.
    implementation(project(":core:domain"))
    // Optional shared utilities (CONVENTIONS.md graph allows :core:common).
    implementation(project(":core:common"))

    // Jetpack Compose (BOM-pinned) + Material 3 — the theming surface.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    // Lifecycle-aware Compose helpers (collectAsStateWithLifecycle, etc.) used by
    // consumers of this theme; bundled here so previews/utilities compile.
    implementation(libs.bundles.lifecycle)

    // ui-tooling is needed at runtime for @Preview rendering in the IDE.
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Logging.
    implementation(libs.timber)

    // Compose UI tests (theme/font switch smoke tests).
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
