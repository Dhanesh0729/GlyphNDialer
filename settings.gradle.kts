pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack — used by some community SDKs (e.g. WebRTC builds).
        maven { url = uri("https://jitpack.io") }
        // Nothing Glyph Developer Kit (GDK) + Glyph Matrix SDK are NOT on a public
        // Maven repo. Obtain the AAR(s) from the Nothing Developer Programme and drop
        // them into peripheral/glyph/libs/. The :peripheral:glyph module pulls them
        // from there via a flatDir fileTree. See CONVENTIONS.md §Glyph and README.
        flatDir { dirs("peripheral/glyph/libs") }
    }
}

rootProject.name = "GlyphDialer"

include(":app")

include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:designsystem")
include(":core:ui")

include(":feature:dialpad")
include(":feature:calllog")
include(":feature:contacts")
include(":feature:incall")
include(":feature:voicemail")
include(":feature:settings")

include(":telecom")

include(":peripheral:glyph")
include(":peripheral:recording")
include(":peripheral:transcription")
include(":peripheral:webrtc")
