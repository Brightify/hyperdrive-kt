import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij)
}

description = "IntelliJ IDEA plugin for Hyperdrive."

dependencies {
    implementation(project(":plugin-impl", configuration = "shadow"))
}

sourceSets.main {
    kotlin.srcDir("../common/src/main/kotlin")
    resources.srcDir("../common/src/main/resources")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.jar {
    manifest {
        attributes["Specification-Title"] = project.name
        attributes["Specification-Version"] = project.version
        attributes["Implementation-Title"] = "org.brightify.hyperdrive"
        attributes["Implementation-Version"] = project.version
    }
}

intellij {
    pluginName.set("hyperdrive")
    version.set("2022.2.3")
    type.set("IC")

    plugins.addAll(
        "gradle",
        "com.intellij.java",
        "org.jetbrains.kotlin",
    )

    updateSinceUntilBuild.set(false)
}

tasks.runPluginVerifier {
    ideVersions.set(
        listOf(
            "IU-222.4345.14",
        )
    )
}

tasks.buildPlugin {
    archiveAppendix.set("IC")
}

tasks.publishPlugin {
    val intellijPublishToken: String? by project
    val intellijChannels: String? by project
    token.set(intellijPublishToken)
    channels.set(listOf(intellijChannels ?: "default"))
}

tasks.buildSearchableOptions {
    enabled = false
}

tasks.patchPluginXml {
    sinceBuild.set("222")
    version.set("${project.version}-IC")
}
