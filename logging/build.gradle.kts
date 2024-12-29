plugins {
    id("hyperdrive-multiplatform")
}

publishingMetadata {
    name = "Hyperdrive Logging"
    description = "Simple logging layer on top of Kermit."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.coroutines.core)
            implementation(libs.kermit.core)
            implementation(projects.kotlinUtils)
        }
    }
}
