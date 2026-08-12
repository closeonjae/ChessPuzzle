// Pure-JVM module: puzzle rules engine + Lichess API client/models.
// No Android dependency here on purpose — keeps this testable with plain
// `./gradlew :core:test`, no emulator/device or Android SDK needed
// (PLAN.md 7절 테스트 전략).
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Repositories are centralized in settings.gradle.kts (dependencyResolutionManagement).

dependencies {
    // api, not implementation: :app's board/UI code needs chesslib's
    // Board/Square/Side/Piece types directly (rendering, tap handling).
    api(libs.chesslib)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
}

// No explicit jvmToolchain(): that forces Gradle to locate/auto-provision a
// matching JDK, which fails offline. Plain JVM module — it just runs on
// whatever JDK is running Gradle itself (verified against the local JBR).

tasks.test {
    useJUnitPlatform()
}
