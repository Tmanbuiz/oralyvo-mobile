// Top-level build file -- plugin versions declared here, applied (not
// re-declared) in module build.gradle.kts files. Keep these two in sync
// with the Kotlin/Compose Compiler versions in app/build.gradle.kts.
//
// Kotlin is 2.0.21, not 1.9.x: the org.jetbrains.kotlin.plugin.compose
// plugin (Compose Compiler as a standalone Gradle plugin) only exists from
// Kotlin 2.0 onward -- on 1.9.x it fails to resolve entirely, which is why
// this is pinned here explicitly rather than left on an older default.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
