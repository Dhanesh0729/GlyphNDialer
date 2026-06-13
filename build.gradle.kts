// Top-level build file. Plugins are declared here with `apply false` so that
// sub-modules can apply them via the version catalog without re-declaring versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Convenience: `./gradlew clean` removes the root build dir.
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
