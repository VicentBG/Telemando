plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.telemando.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.telemando.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
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
