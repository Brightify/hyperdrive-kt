import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("hyperdrive-multiplatform")
    alias(libs.plugins.kotest)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.skie)
}

publishingMetadata {
    name = "Hyperdrive Runtime"
    description = "Hyperdrive implementation that's needed for observations and such"
}

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main") {
            cinterops.create("ObservationRegistrarBridge")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(projects.kotlinUtils)
                api(projects.logging)
                implementation(libs.coroutines.core)
                implementation(compose.runtime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("reflect"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.bundles.kotest.common)
                implementation(libs.coroutines.test)
                implementation(libs.bundles.kotest.jvm)
                implementation(libs.coroutines.test)
            }
        }
    }
}

tasks.dokkaHtmlPartial.configure {
    moduleName.set("Singularity API (${moduleName.get()})")
}
