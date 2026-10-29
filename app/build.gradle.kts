plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing credentials come only from the developer's environment, never from Git.
val releaseKeystore = System.getenv("TELEMANDO_RELEASE_KEYSTORE")
val releasePassword = System.getenv("TELEMANDO_RELEASE_PASSWORD")
val releaseAlias = System.getenv("TELEMANDO_RELEASE_ALIAS")
val releaseKeyPassword = System.getenv("TELEMANDO_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseKeystore, releasePassword, releaseAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.telemando.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.telemando.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"
    }
    signingConfigs {
        if (hasReleaseSigning) create("release") {
            storeFile = file(releaseKeystore!!)
            storePassword = releasePassword
            keyAlias = releaseAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false // Keep the proven protobuf/TLS protocol unchanged in v1.
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

tasks.register("verifyReleaseSigning") {
    doLast {
        check(hasReleaseSigning && file(releaseKeystore!!).isFile) {
            "Release signing is not configured. Set TELEMANDO_RELEASE_* environment variables."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn("verifyReleaseSigning")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("com.google.protobuf:protobuf-javalite:3.25.5")
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
    testImplementation("junit:junit:4.13.2")
}
