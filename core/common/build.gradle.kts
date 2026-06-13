plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.glyphdialer.core.common"
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

    // Explicitly include the Kotlin source root per CONVENTIONS.md §2.
    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
    }
}

// Pure-Kotlin logic in this module is unit-tested with JUnit5 (CONVENTIONS.md §4/§11).
tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    // Coroutines / Flow — the async backbone shared by every module.
    implementation(libs.bundles.coroutines)

    // Dependency injection: this module ships a Hilt @Module (DispatchersModule).
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.javax.inject)

    // Logging.
    implementation(libs.timber)

    // Unit testing (JUnit5 + Truth + Turbine + coroutines-test + MockK).
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
