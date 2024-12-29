package tools.hyperdrive.observable

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

class ObservableObjectIrGenerationExtension(
    private val messageCollector: MessageCollector,
): IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val generator = pluginContext.observableObjectIrGenerator() ?: return

        for (file in moduleFragment.files) {
            generator.runOnFilePostfix(file)
        }
    }

    private fun IrPluginContext.observableObjectIrGenerator(): ObservableObjectIrGenerator? {
        fun logDisabledReason(reason: String): ObservableObjectIrGenerator? {
            messageCollector.report(CompilerMessageSeverity.WARNING, "ViewModel Observe Property generator disabled. Reason: $reason.")
            return null
        }

        val types = ObservableObjectIrGenerator.Types(
            observationRegistrarBridge = ObservationRegistrarBridgeSymbols(this),
            mutableStateSymbols = MutableStateSymbols(this),
        )
        return ObservableObjectIrGenerator(messageCollector, this, types)
    }
}
