plugins {
    id("build-setup")
    kotlin("kapt")
    alias(libs.plugins.kotlin.plugin.serialization)
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            kotlin.setSrcDirs(listOf("src/main/kotlin"))
            resources.setSrcDirs(listOf("src/main/resources"))

            dependencies {
                implementation(project(":multiplatformx-api"))
                implementation(libs.coroutines.core)
                implementation(libs.serialization.protobuf)
            }
        }

        val jvmTest by getting {
            kotlin.setSrcDirs(listOf("src/test/kotlin"))
            resources.setSrcDirs(listOf("src/test/resources"))

            dependencies {
                implementation(libs.coroutines.core)
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))
                implementation(libs.junit.jupiter)
            }
        }
    }
}

kapt {
    includeCompileClasspath = true
}