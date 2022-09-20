pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    plugins {
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.android" || requested.id.name == "kotlin-android-extensions") {
                useModule("com.android.tools.build:gradle:7.2.2")
            }
        }
    }
}

plugins {
    id("io.alcide.gradle-semantic-build-versioning") version "4.2.2"
}

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
    "plugin",
    "compose"
)

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

includeBuild("build-setup")
