plugins {
    id("hyperdrive-multiplatform")
}

publishingMetadata {
    name = "Hyperdrive Annotations"
    description = "Annotations required to configure Hypedrive plugin"
}

kotlin {

}

tasks.dokkaHtmlPartial.configure {
    moduleName.set("Hyperdrive Annotations (${moduleName.get()})")
}
