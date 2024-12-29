package tools.hyperdrive.observable

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getPropertySetter
import org.jetbrains.kotlin.javac.resolve.classId
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

data class MutableStateSymbols(
    val self: IrClassSymbol,
    val mutableStateOfFunction: IrSimpleFunctionSymbol,
    val valueGetter: IrSimpleFunctionSymbol,
    val valueSetter: IrSimpleFunctionSymbol,
) {
    companion object {
        operator fun invoke(context: IrPluginContext): MutableStateSymbols? {
            val self = context.referenceClass(classId("androidx.compose.runtime", "MutableState")) ?: return null

            val mutableStateOfFunction = context.referenceFunctions(
                CallableId(
                    packageName = FqName("androidx.compose.runtime"),
                    callableName = Name.identifier("mutableStateOf"),
                )
            ).singleOrNull() ?: return null

            return MutableStateSymbols(
                self = self,
                mutableStateOfFunction = mutableStateOfFunction,
                valueGetter = self.getPropertyGetter("value")!!,
                valueSetter = self.getPropertySetter("value")!!,
            )

//            import androidx.compose.runtime.MutableState
//                    import androidx.compose.runtime.getValue
//                    import androidx.compose.runtime.mutableStateOf
//                    import androidx.compose.runtime.setValue
        }
    }
}
