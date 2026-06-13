plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.glyphdialer.feature.calllog"
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
    // Clean Architecture dependency graph (CONVENTIONS.md §3):
    // features may depend on core:ui / designsystem / domain / common only.
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))

    // Jetpack Compose (BOM-pinned) + Material 3.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    // Lifecycle-aware Compose helpers (collectAsStateWithLifecycle, viewModel()).
    implementation(libs.bundles.lifecycle)

    // Navigation + Hilt-aware hiltViewModel() / composable() entry points.
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Dependency injection.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Async image loading for real contact photos in list rows.
    implementation(libs.coil.compose)

    // Coroutines + Flow.
    implementation(libs.bundles.coroutines)

    // Logging.
    implementation(libs.timber)

    // ui-tooling at runtime for @Preview rendering in the IDE.
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Pure-logic unit tests (JUnit5 + Turbine + Truth + MockK + coroutines-test).
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit.jupiter.engine)

    // Compose UI tests.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
