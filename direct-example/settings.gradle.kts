pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    plugins {
    }

    includeBuild("../build-setup")
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "direct-example"

includeBuild("..")

include(":shared:compose-android")
include(":shared:compose-multiplatform")
include(":native:android")
