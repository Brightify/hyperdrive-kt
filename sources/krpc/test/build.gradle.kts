import org.brightify.hyperdrive.configurePlatforms
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest

plugins {
    id("build-setup")
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    configurePlatforms(appleSilicon = true)

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":krpc-annotations"))
                api(project(":krpc-shared-api"))
                implementation(project(":logging-api"))

                implementation(libs.bundles.serialization)

                api(libs.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.coroutines.core)
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit5"))

                implementation(libs.junit.jupiter)
            }
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

tasks.withType<KotlinJvmTest> {
    useJUnitPlatform()
}
