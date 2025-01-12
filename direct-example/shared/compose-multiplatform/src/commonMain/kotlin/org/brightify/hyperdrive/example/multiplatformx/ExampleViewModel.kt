package org.brightify.hyperdrive.example.multiplatformx

import tools.hyperdrive.Observable

@Observable
class ExampleViewModel {
    var helloWorld: String = ""

    fun reset() {
        helloWorld = ""
    }
}
