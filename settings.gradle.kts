pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // chesslib is only published to JitPack, not Maven Central.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ChessPuzzle"

include(":core")
include(":app")
