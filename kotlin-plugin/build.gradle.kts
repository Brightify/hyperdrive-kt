plugins {
    id("hyperdrive-kotlin-plugin")
    alias(libs.plugins.shadow)
}

publishingMetadata {
    name = "Hyperdrive Kotlin Plugin"
    description = "Adds support for @ViewModel and @AutoFactory."
}

dependencies {
//    implementation(libs.coroutines.core)

    testImplementation(libs.coroutines.core)
    testImplementation(libs.compile.testing)
}

tasks.shadowJar {
    relocate("org.jetbrains.kotlin.com.intellij", "com.intellij")
    mergeServiceFiles()
    archiveClassifier.set("shadow")
}
