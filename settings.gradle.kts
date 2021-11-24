pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    val kotlinVersion: String by settings
    val kvisionVersion: String by settings
    plugins {
        id("org.jetbrains.dokka") version "1.5.0"
        id("com.github.johnrengelman.shadow") version "7.0.0"
        kotlin("plugin.serialization") version kotlinVersion
        kotlin("multiplatform") version kotlinVersion
        kotlin("jvm") version kotlinVersion
        kotlin("android") version kotlinVersion
        id("com.chromaticnoise.multiplatform-swiftpackage") version "2.0.3"
        id("com.github.gmazzo.buildconfig") version "3.0.1"
        id("org.jetbrains.intellij") version "1.1.2"
        id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
        id("io.kvision") version kvisionVersion
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android" || requested.id.name == "kotlin-android-extensions") {
                useModule("com.android.tools.build:gradle:7.0.0")
            }
        }
    }
}

plugins {
    id("io.alcide.gradle-semantic-build-versioning") version "4.2.2"
}

enableFeaturePreview("VERSION_CATALOGS")

dependencyResolutionManagement {
    val kotlinVersion: String by settings
    val kvisionVersion: String by settings
    versionCatalogs {
        create("libs") {
            version("kotlin", kotlinVersion)
            version("kvision", kvisionVersion)
        }
    }
}

apply(from = "compose-check.gradle.kts")
val isComposeEnabled: Boolean by extra

rootProject.name = "Hyperdrive"

val pluginModules = listOf(
    "api",
    "debug",
    "impl",
    "impl-native",
    "gradle",
    "idea",
)

val pluginProjects = pluginModules.map {
    "plugin-$it" to "plugin/$it"
}

val mainModules = listOf(
    "kotlin-utils"
)

val mainProjects = mainModules.map {
    it to it
}

val krpcModules = listOf(
    "annotations" to emptyList(),
    "shared" to listOf(
        "api",
        "impl",
        "impl-ktor",
    ),
    "client" to listOf(
        "api",
        "impl",
        "impl-ktor",
    ),
    "server" to listOf(
        "api",
        "impl",
        "impl-ktor",
    ),
    "plugin" to emptyList(),
    "integration" to emptyList(),
    "test" to emptyList()
)

val krpcProjects = krpcModules.flatMap {
    val (module, submodules) = it
    if (submodules.isEmpty()) {
        listOf("krpc-$module" to "krpc/$module")
    } else {
        submodules.map { submodule ->
            "krpc-$module-$submodule" to "krpc/$module/$submodule"
        }
    }
}

val loggingModules = listOf(
    "api"
)

val loggingProjects = loggingModules.map { "logging-$it" to "logging/$it" }

val multiplatformXModules = listOf(
    "api",
    "core",
    "plugin"
) + if (isComposeEnabled) listOf("compose") else emptyList()

val multiplatformXProjects = multiplatformXModules.map { "multiplatformx-$it" to "multiplatformx/$it" } + listOf(
    "multiplatformx-keyvalue" to "multiplatformx/keyvalue",
    "multiplatformx-keyvalue-insecure-settings" to "multiplatformx/keyvalue/insecure/settings",
)

val exampleModules = listOf(
    "krpc",
    "multiplatformx"
)

val exampleProjects = exampleModules.map { "example-$it" to "examples/$it" }

val projects = listOf(
    mainProjects,
    pluginProjects,
    krpcProjects,
    multiplatformXProjects,
    exampleProjects,
    loggingProjects
).flatten()

for ((name, path) in projects) {
    include(":$name")
    val project = project(":$name")
    project.projectDir = File(settingsDir, path)
}
