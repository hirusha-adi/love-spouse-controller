// SPDX-FileCopyrightText: 2026 Hirusha Adikari
// SPDX-License-Identifier: MIT

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.hirusha.lscontroller"
    compileSdk = 35
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "dev.hirusha.lscontroller"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.1"
        resourceConfigurations += listOf("en")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // F-Droid builds and signs the same release variant as any other distributor.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

}

kotlin {
    jvmToolchain(17)
}

dependencyLocking {
    lockAllConfigurations()
}

val prepareLicenseAssets by tasks.registering(Sync::class) {
    from(rootProject.file("LICENSE"), rootProject.file("NOTICE"), rootProject.file("LICENSES/Apache-2.0.txt"))
    into(layout.buildDirectory.dir("generated/licenseAssets/licenses"))
}

android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/licenseAssets"))
tasks.named("preBuild") {
    dependsOn(prepareLicenseAssets)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
}
