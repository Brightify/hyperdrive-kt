plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.skie)
}

dependencies {
    kotlinCompilerPluginClasspath("org.brightify.hyperdrive:plugin")
    kotlinNativeCompilerPluginClasspath("org.brightify.hyperdrive:plugin")
}

kotlin {
    jvmToolchain(17)

    androidTarget()
    listOf(
        macosArm64(),
        macosX64(),
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    ).forEach {
        it.binaries {
            framework {
                baseName = "ExampleKit"
            }
        }
    }

    sourceSets.commonMain.dependencies {
        implementation("org.brightify.hyperdrive:annotations")
        implementation("org.brightify.hyperdrive:runtime")

        implementation(libs.coroutines.core)
    }

    sourceSets.commonTest.dependencies {
        implementation(kotlin("test-common"))
        implementation(kotlin("test-annotations-common"))
    }

    val composeBom = dependencies.platform("androidx.compose:compose-bom:2024.06.00")
    sourceSets.androidMain.dependencies {
        implementation(composeBom)

        implementation("androidx.compose.material3:material3")
    }
}

android {
    namespace = "tools.hyperdrive.example.compose_android"

    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
    sourceSets {
        val main by getting
        main.manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
}
