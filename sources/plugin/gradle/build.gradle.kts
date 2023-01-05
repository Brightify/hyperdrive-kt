import com.gradle.publish.PluginBundleExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-gradle-plugin`
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.gradlepublish)
}

buildConfig {
    packageName(project.group.toString())
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"org.brightify.hyperdrive\"")
    buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${project.group}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${project.version}\"")
    buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${project(":plugin-impl").name}\"")
}

pluginBundle {
    website = "https://hyperdrive.tools"
    vcsUrl = "https://github.com/Brightify/hyperdrive-kt"
    tags = listOf("hyperdrive", "kotlin", "multiplatform", "ios", "android")
}

gradlePlugin {
    plugins {
        create("hyperdrive") {
            id = "org.brightify.hyperdrive"
            implementationClass = "org.brightify.hyperdrive.HyperdriveGradlePlugin"
            displayName = "Hyperdrive Gradle Plugin"
            description = "Kotlin Multiplatform extensions plugin."
        }
    }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin-api")
    compileOnly(kotlin("gradle-plugin"))
    implementation(project(":plugin-impl"))

    compileOnly(gradleApi())
    compileOnly(gradleKotlinDsl())

    compileOnly(libs.auto.service)
    kapt(libs.auto.service)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(libs.compile.testing)
    testImplementation(libs.junit.jupiter)
}