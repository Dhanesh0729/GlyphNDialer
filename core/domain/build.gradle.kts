plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.glyphdialer.core.domain"
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

// Domain is pure logic (entities, repo interfaces, use cases). Unit-tested with
// JUnit5 per CONVENTIONS.md §4/§11.
tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    // The only internal dependency the contract allows (CONVENTIONS.md §3).
    implementation(project(":core:common"))

    // Async backbone: Flow for observable reads, suspend for actions.
    implementation(libs.kotlinx.coroutines.core)

    // @Inject on use-case constructors. NOTE: domain stays DI-framework-agnostic and
    // only uses the standard javax.inject annotations — no Hilt here (Hilt @Binds /
    // @Provides live in :core:data and :app where the impls are wired).
    implementation(libs.javax.inject)

    // Logging (CONVENTIONS.md §5/§12): Timber, never android.util.Log.
    implementation(libs.timber)

    // Unit testing (JUnit5 + Truth + Turbine + coroutines-test + MockK).
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
