package tools.hyperdrive.observable

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol

data class ObservationRegistrarBridgeSymbols(
    val self: IrClassSymbol,
    val accessedProperty: IrSimpleFunctionSymbol,
    val willSetProperty: IrSimpleFunctionSymbol,
    val didSetProperty: IrSimpleFunctionSymbol,
) {
    companion object {
        operator fun invoke(context: IrPluginContext): ObservationRegistrarBridgeSymbols? {
            val self = context.referenceClass(Names.Types.observationRegistrarBridge) ?: return null
            return ObservationRegistrarBridgeSymbols(
                self = self,
                accessedProperty = self.functionByName("accessedProperty"),
                willSetProperty = self.functionByName("willSetProperty"),
                didSetProperty = self.functionByName("didSetProperty"),
            )
        }
    }
}
