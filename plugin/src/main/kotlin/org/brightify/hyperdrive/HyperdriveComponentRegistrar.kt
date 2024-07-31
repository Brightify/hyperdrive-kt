package org.brightify.hyperdrive

import org.brightify.hyperdrive.autofactory.AutoFactoryIrGenerationExtension
import org.brightify.hyperdrive.autofactory.AutoFactoryResolveExtension
import org.brightify.hyperdrive.autofactory.HyperdriveFirExtensionRegistrar
import org.brightify.hyperdrive.viewmodel.ViewModelIrGenerationExtension
import org.brightify.hyperdrive.viewmodel.ViewModelResolveExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension

@OptIn(ExperimentalCompilerApi::class)
class HyperdriveComponentRegistrar: CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val messageCollector = configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        messageCollector.report(CompilerMessageSeverity.WARNING, "Hyperdrive is experimental!")

        registerAutoFactoryExtensions()

        registerViewModelExtensions(messageCollector)
    }

    private fun ExtensionStorage.registerAutoFactoryExtensions() {
//        SyntheticResolveExtension.registerExtension(AutoFactoryResolveExtension())
//        FirExtensionRegistrarAdapter.registerExtension(HyperdriveFirExtensionRegistrar())
//        IrGenerationExtension.registerExtension(AutoFactoryIrGenerationExtension())
    }

    private fun ExtensionStorage.registerViewModelExtensions(
        messageCollector: MessageCollector,
    ) {
        SyntheticResolveExtension.registerExtension(ViewModelResolveExtension())
        IrGenerationExtension.registerExtension(ViewModelIrGenerationExtension(messageCollector))
    }
}
