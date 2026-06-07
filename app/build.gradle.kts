import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// Apply the Google Services plugin only when a google-services.json is present.
// This lets the project build & run (with in-app realtime notifications) before
// Firebase is configured; drop the file in app/ to light up background FCM push.
val googleServicesJson = file("google-services.json")
if (googleServicesJson.exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

fun resolveProp(key: String): String {
    val fromLocal = localProps[key]?.toString()
    if (!fromLocal.isNullOrBlank()) return fromLocal
    val fromGradle = project.findProperty(key)?.toString()
    if (!fromGradle.isNullOrBlank()) return fromGradle
    return System.getenv(key) ?: ""
}

android {
    namespace = "com.example.fixbid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fixbid"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${resolveProp("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_API_KEY", "\"${resolveProp("SUPABASE_API_KEY")}\"")

        // VNPay Sandbox
        buildConfigField("String", "VNPAY_TMN_CODE", "\"${resolveProp("VNPAY_TMN_CODE")}\"")
        buildConfigField("String", "VNPAY_HASH_SECRET", "\"${resolveProp("VNPAY_HASH_SECRET")}\"")

        // Groq AI (chatbot). Key lives in local.properties, never hardcoded.
        buildConfigField("String", "GROQ_API_KEY", "\"${resolveProp("GROQ_API_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // jqwik runs on the JUnit Platform; tell the unit-test task to use it so
    // @Property tests are discovered alongside legacy JUnit 4 tests.
    testOptions {
        unitTests.all {
            it.useJUnitPlatform {
                includeEngines("jqwik", "junit-jupiter", "junit-vintage")
            }
        }
    }
}

dependencies {
    // Kotlin Core
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Lifecycle + Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.1"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")

    implementation("io.ktor:ktor-client-okhttp:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("io.ktor:ktor-client-logging:3.0.3")

    // Datastore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Markdown rendering for AI assistant bubbles. Renders Markdown to Compose
    // text — supports bold, italic, lists, code spans, links. Lightweight
    // (no Markwon transitive android-only Java weight) and Compose-native.
    implementation("com.github.jeziellago:compose-markdown:0.5.4")

    // Maps & Navigation — MapLibre Native rendering OpenStreetMap raster tiles.
    // Routing comes from the public OSRM demo server (see RoutingService); both
    // are fully open-source and require no API key, matching the project's
    // "no Google/Mapbox lock-in" stance.
    implementation("org.maplibre.gl:android-sdk:11.5.1")

    // Fused Location (worker current location)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Permissions helper for Compose
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Firebase Cloud Messaging (background/killed-app push). The BOM keeps the
    // messaging artifact version aligned. Active once google-services.json is added.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    // Bridges the Play Services Task<String> token fetch into a coroutine.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Test — JUnit 4 (legacy) + JUnit 5 (jqwik) + jqwik PBT + Testcontainers Postgres
    testImplementation(libs.junit)

    // jqwik (property-based testing) runs on the JUnit Platform engine.
    testImplementation("net.jqwik:jqwik:1.9.3")
    testImplementation("net.jqwik:jqwik-kotlin:1.9.3")

    // Postgres testcontainer + JDBC driver — boots a real Postgres for SQL/RPC PBT.
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.postgresql:postgresql:42.7.4")

    // JUnit 5 platform (engine + launcher) so jqwik tests run under `./gradlew test`.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    // Vintage engine keeps the legacy JUnit 4 ExampleUnitTest running under the
    // JUnit Platform alongside jqwik / Jupiter tests.
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}