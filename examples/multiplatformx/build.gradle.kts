import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME

plugins {
    id("com.android.library")
    kotlin("multiplatform")
}

dependencies {
    PLUGIN_CLASSPATH_CONFIGURATION_NAME(project(":plugin-impl"))
    NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME(project(":plugin-impl-native"))
}

android {
    compileSdkVersion(30)

    dexOptions {
        javaMaxHeapSize = "2g"
    }

    defaultConfig {
        minSdkVersion(16)
    }

    sourceSets {
        val main by getting
        main.manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

kotlin {
    jvm()
    android()
    ios()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":multiplatformx-annotations"))
                implementation(project(":multiplatformx-api"))

                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-junit"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val androidMain by getting
        val androidTest by getting

        val iosMain by getting
        val iosTest by getting
    }

    sourceSets.all {
        languageSettings.apply {
            progressiveMode = true
        }
    }

    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs += listOf(
                    "-P", "plugin:org.brightify.hyperdrive:multiplatformx.enabled=true",
                    "-P", "plugin:org.brightify.hyperdrive:multiplatformx.viewmodel.enabled=true",
                    "-P", "plugin:org.brightify.hyperdrive:multiplatformx.autofactory.enabled=true"
                )
            }
        }
    }
}
