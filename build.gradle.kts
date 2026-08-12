// Root build file: declares plugin versions once (via the version catalog) so
// the :core and :app modules can `apply false` / `apply true` them without
// re-specifying versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
