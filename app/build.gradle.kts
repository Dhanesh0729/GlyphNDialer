// SPDX-License-Identifier: Apache-2.0
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Optional release signing read from a (git-ignored) keystore.properties at the repo
// root. Guarded so debug builds and CI without the file still configure cleanly.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystoreProps.getProperty("storeFile")?.isNotBlank() == true

android {
    namespace = "com.glyphdialer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.glyphdialer"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Default Glyph/Nothing API key. Debug builds may use "test" (BUILD_SPEC §17.1);
        // override @string/nothing_api_key with a real key for production. The glyph
        // module declares the NothingKey meta-data that reads this resource.
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            // R8 full-mode minification + resource shrinking for the release APK.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use the release signing config only when keystore.properties is present;
            // otherwise fall back to debug signing so `assembleRelease` never fails on a
            // dev machine / fresh checkout (TODO: provide keystore.properties to ship).
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time + other desugared APIs on minSdk 29.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Kotlin sources live under src/main/kotlin (CONVENTIONS.md §2). AGP's default
    // source set is src/main/java; add the kotlin dir explicitly to be safe.
    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
        getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
    }

    packaging {
        resources {
            // De-dupe license/notice files pulled in transitively (ktor, webrtc, etc.).
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
            )
        }
    }
}

dependencies {
    // :app wires the whole graph (CONVENTIONS.md §3) — depend on every module.
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))

    implementation(project(":feature:dialpad"))
    implementation(project(":feature:calllog"))
    implementation(project(":feature:contacts"))
    implementation(project(":feature:incall"))
    implementation(project(":feature:voicemail"))
    implementation(project(":feature:settings"))

    implementation(project(":telecom"))

    implementation(project(":peripheral:glyph"))
    implementation(project(":peripheral:recording"))
    implementation(project(":peripheral:transcription"))
    implementation(project(":peripheral:webrtc"))

    // AndroidX core
    implementation(libs.androidx.core.ktx)

    // Material Components — only for the XML launch/splash theme parent
    // (Theme.Material3.DayNight). The live UI is Compose Material 3 from the design system.
    implementation(libs.material)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Coroutines
    implementation(libs.bundles.coroutines)

    // DI (Hilt) + Hilt for Compose nav + WorkManager
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.work.runtime.ktx)

    // Runtime permissions
    implementation(libs.accompanist.permissions)

    // Logging
    implementation(libs.timber)

    // Core library desugaring (java.time on minSdk 29).
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Unit tests (JUnit5 + Turbine + Truth + MockK; CONVENTIONS.md §11).
    testImplementation(libs.bundles.unit.test)
    testRuntimeOnly(libs.junit.jupiter.engine)

    // Instrumented / Compose UI tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
