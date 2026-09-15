// AGP 9's built-in Kotlin support (app/build.gradle.kts no longer applies
// org.jetbrains.kotlin.android) defaults to whatever Kotlin Gradle Plugin AGP itself
// bundles, NOT the `kotlin` version in libs.versions.toml — override it here so the
// actual compiler matches what kotlin.plugin.compose/kotlin.plugin.serialization/ksp
// are built against (a mismatch here is what caused KSP's Room step to fail with
// "unexpected jvm signature V").
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
