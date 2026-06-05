plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20" apply false
    id("com.google.dagger.hilt.android") version "2.56.2" apply false
    id("com.google.devtools.ksp") version "2.3.2" apply false
    // FCM: applied conditionally in :app only when google-services.json exists,
    // so the project still builds before Firebase is configured.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
