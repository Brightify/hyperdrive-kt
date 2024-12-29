package tools.hyperdrive.observable

import org.jetbrains.kotlin.javac.resolve.classId

object Names {
    object Annotations {
        val observable = classId("tools.hyperdrive", "Observable")
        val observationTracked = classId("tools.hyperdrive", "ObservationTracked")
        val observationIgnored = classId("tools.hyperdrive", "ObservationIgnored")
    }

    object Types {
        val observationRegistrarBridge = classId("tools.hyperdrive", "ObservationRegistrarBridge")
        val mutableState = classId("androidx.compose.runtime", "MutableState")
    }
}
