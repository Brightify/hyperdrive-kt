pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
    }

    includeBuild("build-setup")
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Hyperdrive"

include(
    ":annotations",
    ":kotlin-utils",
    ":logging",
    ":gradle-plugin",
    ":kotlin-plugin",
    ":runtime",
)
