plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.shared.composeAndroid)
}

android {
    namespace = "tools.hyperdrive.example"

    compileSdk = 34
    defaultConfig {
        applicationId = "tools.hyperdrive.example"

        minSdk = 21
    }
//    sourceSets {
//        val main by getting
//        main.manifest
//    }
//    compileOptions {
//        sourceCompatibility = JavaVersion.VERSION_1_8
//        targetCompatibility = JavaVersion.VERSION_1_8
//    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = dependencies.platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.activity:activity")
    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
