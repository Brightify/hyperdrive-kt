import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("hyperdrive-base")
    kotlin("multiplatform")
    id("org.jetbrains.dokka")
    id("hyperdrive-publishable")
}

val libs = the<LibrariesForLibs>()

kotlin {
    explicitApi()

    jvm()

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    tvosArm64()
    tvosSimulatorArm64()
    tvosX64()

    macosX64()
    macosArm64()

    watchosDeviceArm64()
    watchosSimulatorArm64()
    watchosX64()

    js(IR) {
        browser {
            testTask {
                enabled = false
            }
        }
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.junit.jupiter)
            }
        }
    }
}
