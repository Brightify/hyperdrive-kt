import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
}

tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

repositories {
    mavenCentral()
    jcenter()
    google()
    gradlePluginPortal()
}

dependencies {
    api(project(":plugin-api"))
    implementation(kotlin("stdlib"))
    compileOnly(kotlin("compiler-embeddable"))
    implementation(kotlin("serialization"))
    implementationWorkaround(project(":krpc-shared-api"))
    implementationWorkaround(project(":krpc-client-api"))
    implementationWorkaround(project(":krpc-annotations"))

    compileOnly("com.google.auto.service:auto-service:${Versions.autoService}")
    kapt("com.google.auto.service:auto-service:${Versions.autoService}")

    testImplementationWorkaround(project(":krpc-shared-api"))
    testImplementationWorkaround(project(":krpc-shared-impl"))
    testImplementationWorkaround(project(":krpc-server-api"))
    testImplementationWorkaround(project(":krpc-server-impl"))
    testImplementationWorkaround(project(":krpc-client-api"))
    testImplementationWorkaround(project(":krpc-client-impl"))
    testImplementationWorkaround(project(":krpc-annotations"))
    testImplementationWorkaround(project(":krpc-test"))

    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.serialization}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:${Versions.serialization}")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing:1.3.6")
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
}

fun DependencyHandlerScope.implementationWorkaround(dependency: ProjectDependency) {
    implementation(dependency)
    val project = dependency.dependencyProject
    compileOnly(
        files(File(project.buildDir, "libs/${project.name}-jvm-${project.version}.jar"))
    )
}

fun DependencyHandlerScope.testImplementationWorkaround(dependency: ProjectDependency) {
    testImplementation(dependency)
    val project = dependency.dependencyProject
    testCompileOnly(
        files(File(project.buildDir, "libs/${project.name}-jvm-${project.version}.jar"))
    )
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}