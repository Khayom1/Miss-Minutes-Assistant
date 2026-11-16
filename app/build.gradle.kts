plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val supabaseUrl = providers.gradleProperty("supabaseUrl")
    .orElse("https://bgfcpnlxztziigrsyuqp.supabase.co")
    .get()
val supabasePublishableKey = providers.gradleProperty("supabasePublishableKey")
    .orElse("sb_publishable_EZp3LgM4zcqtFKBUkphGaQ_FmWpyOWz")
    .get()
val backendProxyUrl = providers.gradleProperty("backendProxyUrl")
    .orElse("${supabaseUrl.trimEnd('/')}/functions/v1/deepseek-proxy")
    .get()

require(supabaseUrl.startsWith("https://")) { "supabaseUrl must use HTTPS" }
require(backendProxyUrl.startsWith("https://")) { "backendProxyUrl must use HTTPS" }

android {
    namespace = "com.khayom1.missminutes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.khayom1.missminutes"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", supabaseUrl.asBuildConfigString())
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", supabasePublishableKey.asBuildConfigString())
        buildConfigField("String", "BACKEND_PROXY_URL", backendProxyUrl.asBuildConfigString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    androidTestImplementation(platform("androidx.compose:compose-bom:2025.12.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
