package org.brightify.hyperdrive.viewmodel

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrPropertyReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrFieldSymbolImpl
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name
import java.util.*

class ViewModelIrGenerator(
    private val pluginContext: IrPluginContext,
    private val types: Types,
): IrElementTransformerVoid(), ClassLoweringPass {

    class Types(
        val lazy: IrClassSymbol,
        val lazyValue: IrSimpleFunctionSymbol,
        val observe: IrSimpleFunctionSymbol,
        val mutableObserve: IrSimpleFunctionSymbol,
    )

    @OptIn(ObsoleteDescriptorBasedAPI::class)
    override fun lower(irClass: IrClass) {
        if (!irClass.hasAnnotation(ViewModelNames.Annotation.viewModel.asSingleFqName())) { return }

        val observePropertyIndices = mutableSetOf<Int>()
        for ((index, declaration) in irClass.declarations.withIndex()) {
            val property = declaration as? IrProperty ?: continue
            // Ignore overriden symbols
            if (property.overriddenSymbols.isNotEmpty()) { continue }
            val referencedPropertyName = NamingHelper.getReferencedPropertyName(property.name.identifier) ?: continue
            val propertyGetter = property.getter ?: continue
            if (propertyGetter.body != null) { continue }

            val referencedProperty = irClass.properties.singleOrNull { classProperty ->
                classProperty.name.identifier == referencedPropertyName
            } ?: continue

            val declarationBuilder = DeclarationIrBuilder(pluginContext, propertyGetter.symbol)
            val delegateField = pluginContext.irFactory.createField(
                startOffset = property.startOffset,
                endOffset = property.endOffset,
                origin = IrDeclarationOrigin.PROPERTY_DELEGATE,
                symbol = IrFieldSymbolImpl(property.descriptor),
                name = Name.identifier("${property.name.identifier}\$delegate"),
                type = types.lazy.typeWith(propertyGetter.returnType),
                visibility = DescriptorVisibilities.PRIVATE,
                isFinal = true,
                isExternal = false,
                isStatic = false,
            ).also { field ->
                val (observeMethod, kpropertySymbol) = if (referencedProperty.isVar) {
                    types.mutableObserve to pluginContext.symbols.kmutableproperty0()
                } else {
                    types.observe to pluginContext.symbols.kproperty0()
                }

                field.parent = irClass
                field.initializer = declarationBuilder.irExprBody(
                    declarationBuilder.irCall(observeMethod, field.type).apply {
                        putTypeArgument(0, referencedProperty.getter!!.returnType)
                        dispatchReceiver = irClass.thisReceiver?.let { declarationBuilder.irGet(it) }
                        putValueArgument(0,
                            IrPropertyReferenceImpl(
                                referencedProperty.startOffset,
                                referencedProperty.endOffset,
                                kpropertySymbol.typeWith(referencedProperty.getter!!.returnType),
                                referencedProperty.symbol,
                                0,
                                null,
                                referencedProperty.getter?.symbol,
                                referencedProperty.setter?.symbol,
                                null
                            ).apply {
                                dispatchReceiver = irClass.thisReceiver?.let { declarationBuilder.irGet(it) }
                            }
                        )
                    }
                )
            }
            property.backingField = delegateField
            propertyGetter.body = declarationBuilder.irBlockBody {
                +irReturn(
                    irCall(types.lazyValue, propertyGetter.returnType).apply {
                        dispatchReceiver = irGetField(propertyGetter.dispatchReceiverParameter?.let { irGet(it) }, delegateField)
                    }
                )
            }
            observePropertyIndices.add(index)
        }

        val observableDeclarations = ArrayList<IrDeclaration>(observePropertyIndices.count())
        val otherDeclarations = ArrayList<IrDeclaration>(irClass.declarations.count() - observePropertyIndices.count())
        for ((index, declaration) in irClass.declarations.withIndex()) {
            if (observePropertyIndices.contains(index)) {
                observableDeclarations.add(declaration)
            } else {
                otherDeclarations.add(declaration)
            }
        }
        irClass.declarations.clear()
        irClass.addAll(observableDeclarations)
        irClass.addAll(otherDeclarations)
    }
}
