plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.khayom1.missminutes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.khayom1.missminutes"
        minSdk = 34
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Keep patched transitive dependencies aligned in production and test variants.
    implementation(platform("io.netty:netty-bom:4.1.137.Final"))
    testImplementation(platform("io.netty:netty-bom:4.1.137.Final"))
    androidTestImplementation(platform("io.netty:netty-bom:4.1.137.Final"))
    constraints {
        implementation("com.google.guava:guava:33.8.0-jre")
        implementation("com.google.protobuf:protobuf-java:3.25.9")
        implementation("com.google.protobuf:protobuf-kotlin:3.25.9")
        testImplementation("com.google.guava:guava:33.8.0-jre")
        testImplementation("com.google.protobuf:protobuf-java:3.25.9")
        testImplementation("com.google.protobuf:protobuf-kotlin:3.25.9")
        androidTestImplementation("com.google.guava:guava:33.8.0-jre")
        androidTestImplementation("com.google.protobuf:protobuf-java:3.25.9")
        androidTestImplementation("com.google.protobuf:protobuf-kotlin:3.25.9")
    }

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(platform("androidx.compose:compose-bom:2026.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.work:work-testing:2.10.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
