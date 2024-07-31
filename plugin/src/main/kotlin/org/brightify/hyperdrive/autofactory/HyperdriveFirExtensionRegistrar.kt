package org.brightify.hyperdrive.autofactory

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class HyperdriveFirExtensionRegistrar: FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::AutoFactoryMembersGenerator
    }
}
