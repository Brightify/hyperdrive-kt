plugins {
    id("hyperdrive-android")
    alias(libs.plugins.compose.compiler)
}

publishingMetadata {
    name = "Hyperdrive Compose"
    description = "Helpers for using Hyperdrive from Compose UI"
}

val kotlinVersion: String by project

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

android {
    compileSdk = 30
    namespace = "org.brightify.hyperdrive"

    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
//        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

}

dependencies {
    implementation(projects.runtime)
    implementation(libs.compose.runtime)

    androidTestImplementation(libs.compose.material)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
            }
            create<MavenPublication>("debug") {
                from(components["debug"])

                artifactId = project.name + "-debug"
            }
        }
    }
}
