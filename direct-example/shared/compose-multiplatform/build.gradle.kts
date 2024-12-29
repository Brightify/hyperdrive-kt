plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.skie)
}

dependencies {
    kotlinCompilerPluginClasspath("org.brightify.hyperdrive:kotlin-plugin")
    kotlinNativeCompilerPluginClasspath("org.brightify.hyperdrive:kotlin-plugin")
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

        implementation(compose.runtime)
        implementation(compose.material)
    }
}

android {
    namespace = "tools.hyperdrive.example.compose_multiplatform"

    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
    sourceSets {
        val main by getting
        main.manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
}
