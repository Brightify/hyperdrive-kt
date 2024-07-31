import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper

plugins {
    `maven-publish`
    signing
}

val publishingMetadata = extensions.create<PublishingMetadata>("publishingMetadata")

publishing.publications.withType<MavenPublication>().configureEach {
    pom {
        name.set(publishingMetadata.name)
        description.set(publishingMetadata.description)
        url.set("https://hyperdrive.tools")
        licenses {
            license {
                name.set("MIT License")
                url.set("http://www.opensource.org/licenses/mit-license.php")
            }
        }
        developers {
            developer {
                id.set("TadeasKriz")
                name.set("Tadeas Kriz")
                email.set("me@tadeaskriz.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/Brightify/Hyperdrive.git")
            developerConnection.set("scm:git:ssh://github.com:Brightify/Hyperdrive.git")
            url.set("http://github.com/Brightify/Hyperdrive/tree/main")
        }
    }
}

afterEvaluate {
    if (plugins.hasPlugin(KotlinPluginWrapper::class)) {
        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
            withJavadocJar()
        }
        publishing {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                }
            }
        }
    }
    if (plugins.hasPlugin(KotlinMultiplatformPluginWrapper::class)) {
        publishing.publications.withType<MavenPublication> {
            val publication = this
            val javadocJar = tasks.register<Jar>("${publication.name}JavadocJar") {
                archiveClassifier.set("javadoc")
                archiveBaseName.set("${archiveBaseName.get()}-${publication.name}")
            }
            artifact(javadocJar)
        }
    }
}

signing {
    setRequired({
        gradle.taskGraph.hasTask("publishToSonatype")
    })

//    val signingKeyId: String? by project
//    val signingKey: String? by project
//    val signingPassword: String? by project
//    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
}

publishing.publications.withType<MavenPublication>().configureEach {
    signing.sign(this)
}
