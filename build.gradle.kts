plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
    // Registered with a version (and apply false) so that app/build.gradle.kts
    // can refer to it by id alone and apply it conditionally at runtime via
    // `apply(plugin = "com.google.gms.google-services")` only when a
    // google-services.json is present. Without a version declared somewhere
    // on the root classpath, the plugins block fails to resolve.
    id("com.google.gms.google-services") version "4.4.4" apply false
}
