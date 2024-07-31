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
    ":compose",
//    ":ide:android-studio",
//    ":ide:intellij-idea",
    ":kotlin-utils",
    ":logging",
    ":plugin",
    ":runtime",
)
