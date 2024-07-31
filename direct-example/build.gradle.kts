import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

//import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
//import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
//import org.jetbrains.kotlin.gradle.tasks.FatFrameworkTask
//import org.jetbrains.kotlin.gradle.plugin.mpp.Framework.BitcodeEmbeddingMode

plugins {
//    id("com.android.library")
//    kotlin("multiplatform")
    alias(libs.plugins.kotlin.multiplatform)
//    alias(libs.plugins.swiftpackage)
//    id("org.brightify.hyperdrive")
}

//multiplatformSwiftPackage {
//    swiftToolsVersion("5.3")
//    targetPlatforms {
//        iOS { v("13") }
//    }
//}

//hyperdrive {
//    multiplatformx {
//        isViewModelEnabled = true
//        isAutoFactoryEnabled = true
//    }
//}

//android {
//    compileSdkVersion(30)
//
//    dexOptions {
//        javaMaxHeapSize = "2g"
//    }
//
//    defaultConfig {
//        minSdkVersion(16)
//    }
//
//    sourceSets {
//        val main by getting
//        main.manifest.srcFile("src/androidMain/AndroidManifest.xml")
//    }
//    compileOptions {
//        sourceCompatibility = JavaVersion.VERSION_1_8
//        targetCompatibility = JavaVersion.VERSION_1_8
//    }
//}

dependencies {
//    kotlinCompilerPluginClasspath(project(":plugin"))
}

kotlin {
    jvm()
    listOf(
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

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.brightify.hyperdrive:runtime")

                implementation(libs.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
    }
}
