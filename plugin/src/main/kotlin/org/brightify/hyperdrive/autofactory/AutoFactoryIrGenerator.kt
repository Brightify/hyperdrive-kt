package org.brightify.hyperdrive.autofactory

import org.brightify.hyperdrive.Either
import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

open class AutoFactoryIrGenerator(
    private val compilerContext: IrPluginContext
): IrElementTransformerVoid(), ClassLoweringPass {
    override fun lower(irClass: IrClass) {
        if (irClass.name != AutoFactoryNames.factory) { return }

        val autoFactoryConstructor = irClass.parentAutoFactoryConstructor ?: return

        irClass.transformChildren(AutoFactoryFakeOverrideTransformer(anyClass = compilerContext.irBuiltIns.anyClass), null)

        val factoryConstructor = irClass.constructors.single { it.visibility == DescriptorVisibilities.PUBLIC }
        val factoryPrimaryConstructor = irClass.primaryConstructor!!
        val factoryCreateMethod = irClass.functions.single { it.name == AutoFactoryNames.createFun }
        // irClass.addFakeOverrides(compilerContext.irBuiltIns, listOf(factoryCreateMethod))

//        0
//
//        8
        // IrOverridingUtil(compilerContext.irBuiltIns, FakeOverrideBuilder())

        val newParametersAccess: List<Either<IrValueParameter, Pair<IrField, IrValueParameter>>> = autoFactoryConstructor.valueParameters.map { parameter ->
            if (parameter.hasAnnotation(AutoFactoryNames.Annotation.provided)) {
                val newParameter = factoryCreateMethod.valueParameters.single { it.name == parameter.name }
                Either.Left<IrValueParameter, Pair<IrField, IrValueParameter>>(newParameter)
            } else {
                val field = irClass.addField {
                    updateFrom(parameter)
                    name = parameter.name
                    type = parameter.type
                    visibility = DescriptorVisibilities.PRIVATE
                }
                val constructorParameter = factoryConstructor.valueParameters.single { it.name == parameter.name }
                Either.Right<IrValueParameter, Pair<IrField, IrValueParameter>>(field to constructorParameter)
            }
        }

        factoryConstructor.body = DeclarationIrBuilder(compilerContext, factoryConstructor.symbol).irBlockBody {
            +irDelegatingConstructorCall(
                if (factoryConstructor == factoryPrimaryConstructor) {
                    compilerContext.symbols.any.constructors.first().owner
                } else {
                    factoryPrimaryConstructor
                }
            )

            for (newParameterAccess in newParametersAccess) {
                if (newParameterAccess is Either.Right) {
                    +irSetField(irGet(irClass.thisReceiver!!), newParameterAccess.value.first, irGet(newParameterAccess.value.second))
                }
            }
        }

        factoryCreateMethod.body = DeclarationIrBuilder(compilerContext, factoryCreateMethod.symbol).irBlockBody {
            +irReturn(
                irConstructorCall(
                    irCall(autoFactoryConstructor).also { call ->
                        for ((index, newParameterAccess) in newParametersAccess.withIndex()) {
                            when (newParameterAccess) {
                                is Either.Left -> {
                                    call.putValueArgument(index, irGet(newParameterAccess.value))
                                }
                                is Either.Right -> {
                                    call.putValueArgument(index, irGetField(irGet(factoryCreateMethod.dispatchReceiverParameter!!), newParameterAccess.value.first))
                                }
                            }
                        }
                    },
                    autoFactoryConstructor.symbol
                )
            )
        }
    }
}
