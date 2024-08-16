package tools.hyperdrive.example

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCMethod
import org.brightify.hyperdrive.example.multiplatformx.ExampleViewModel
import tools.hyperdrive.ObservableObject
import tools.hyperdrive.ObservationRegistrar
import kotlin.reflect.KProperty1
import tools.hyperdrive.ObservationRegistrarBridge
//import tools.hyperdrive.example.IosExampleViewModelAccess

@OptIn(ExperimentalForeignApi::class)
class IosExampleViewModel: ObservableObject {

    val _observationRegistrar = ObservationRegistrarBridge()

    var helloWorld: String = ""
        get() {
            _observationRegistrar.accessedProperty("helloWorld")
            return field
        }
        set(value) {
            _observationRegistrar.willSetProperty("helloWorld")
            field = value
            _observationRegistrar.didSetProperty("helloWorld")
        }
}
