package tools.hyperdrive.observable

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.addMember
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrFieldSymbolImpl
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getPropertySetter
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.javac.resolve.classId
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.math.exp

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

class ObservableObjectIrGenerator(
    private val messageCollector: MessageCollector,
    private val pluginContext: IrPluginContext,
    private val types: Types,
): IrElementTransformerVoid(), ClassLoweringPass {

    class Types(
        val observationRegistrarBridge: ObservationRegistrarBridgeSymbols?,
        val mutableStateSymbols: MutableStateSymbols?,
    )

    override fun lower(irClass: IrClass) {
        if (!irClass.hasAnnotation(Names.Annotations.observable)) { return }

        if (types.mutableStateSymbols == null && types.observationRegistrarBridge == null) {
            messageCollector.report(
                severity = CompilerMessageSeverity.STRONG_WARNING,
                message = "No observation runtime found, @Observable annotated class ${irClass.name} won't be changed.",
            )
            return
        }

        irClass.markPropertiesForTracking()

        if (types.mutableStateSymbols != null) {
            irClass.delegatePropertiesToState(types.mutableStateSymbols)
        }

        if (types.observationRegistrarBridge != null) {
            val observationRegistrarField = irClass.addObservationRegistrarBridge(types.observationRegistrarBridge)
            irClass.wrapMutableProperties(types.observationRegistrarBridge, observationRegistrarField)
        }
    }

    private fun IrClass.markPropertiesForTracking() {
        declarations.filterIsInstance<IrProperty>()
            .filter { it.isVar && !it.hasAnnotation(Names.Annotations.observationIgnored) }
            .forEach {
                // TODO: Add `@ObservationTracked`
//                it.annotations += irConstructorCall(
//                    call = ,
//                    newSymbol = ,
//                    receiversAsArguments = ,
//                    argumentsAsDispatchers =
//                )
            }
    }

    private fun IrClass.delegatePropertiesToState(mutableState: MutableStateSymbols) {
        declarations.filterIsInstance<IrProperty>()
            .filter { it.isVar }
            .forEach { property ->
                property.delegateToState(mutableState)
            }
    }

    private fun IrProperty.delegateToState(mutableState: MutableStateSymbols) {
        if (backingField == null) {
            error("$this has null `backingField`")
        }

        val declarationBuilder = DeclarationIrBuilder(pluginContext, symbol)
        val originalType = backingField!!.type
        backingField!!.type = mutableState.self.typeWith(originalType)
        println("Changed backingField from ${originalType.classFqName} to ${backingField!!.type.classFqName}")

        val originalInitializer = backingField!!.initializer!!
        backingField!!.initializer = declarationBuilder.irExprBody(
            declarationBuilder.irCall(mutableState.mutableStateOfFunction).apply {
                putTypeArgument(0, originalType)
                putValueArgument(0, originalInitializer.expression)
            }
        )

        getter!!.transformChildrenVoid(object: IrElementTransformerVoid() {
            override fun visitGetField(expression: IrGetField): IrExpression {
                println("[get] ${expression.symbol == backingField!!.symbol}")
                return if (expression.symbol == backingField!!.symbol) {
                    declarationBuilder.irCall(
                        mutableState.valueGetter,
                    ).apply {
                        this.dispatchReceiver = expression
                    }
                } else {
                    super.visitGetField(expression)
                }
            }
        })

        setter!!.transformChildrenVoid(object: IrElementTransformerVoid() {
            override fun visitSetField(expression: IrSetField): IrExpression {
                println("[set] ${expression.symbol == backingField!!.symbol}")
                return if (expression.symbol == backingField!!.symbol) {
                    declarationBuilder.irCall(
                        mutableState.valueSetter,
                    ).apply {
                        this.dispatchReceiver = declarationBuilder.irGetField(
                            expression.receiver, backingField!!
                        )
                        putValueArgument(0, expression.value)
                    }
                } else {
                    super.visitSetField(expression)
                }
            }
        })
    }

    private fun IrClass.addObservationRegistrarBridge(bridge: ObservationRegistrarBridgeSymbols): IrField {
        val declarationBuilder = DeclarationIrBuilder(pluginContext, symbol)
        val field = pluginContext.irFactory.createField(
            startOffset = 0,
            endOffset = 0,
            origin = IrDeclarationOrigin.SYNTHETIC_ACCESSOR,
            name = Name.identifier("_\$observationRegistrar"),
            visibility = DescriptorVisibilities.PRIVATE,
            symbol = IrFieldSymbolImpl(),
            type = bridge.self.defaultType,
            isFinal = true,
            isStatic = false,
            isExternal = false,
        ).also { field ->
            field.parent = this
            field.initializer = declarationBuilder.irExprBody(
                declarationBuilder.irCallConstructor(
                    bridge.self.constructors.first(),
                    emptyList(),
                )
            )
        }
        this.addMember(field)
        return field
    }

    private fun IrClass.wrapMutableProperties(
        bridge: ObservationRegistrarBridgeSymbols,
        observationRegistrarField: IrField,
    ) {
        declarations.filterIsInstance<IrProperty>()
            // TODO: Only consider properties without `@ObservationIgnored`
            .filter { it.isVar }
            .forEach { property ->
                println("wrapAccess: ${property.name}")
                property.wrapAccess(bridge, observationRegistrarField)
            }
    }

    private fun IrProperty.wrapAccess(bridge: ObservationRegistrarBridgeSymbols, observationRegistrarField: IrField) {
        val declarationBuilder = DeclarationIrBuilder(pluginContext, symbol)
        // TODO: Replace getter to use `access` (generated by `addAccessMethod`)
        println("Getter: $getter")
        val originalGetterBody = getter!!.body!!
        val getterStatements = if (originalGetterBody is IrBlockBody) {
            originalGetterBody.statements
        } else {
            pluginContext.irFactory.createBlockBody(
                startOffset = originalGetterBody.startOffset,
                endOffset = originalGetterBody.endOffset,
                statements = originalGetterBody.statements,
            ).statements
        }

        getterStatements.add(
            0,
            declarationBuilder.irCall(
                callee = bridge.accessedProperty,
            ).apply {
                dispatchReceiver = declarationBuilder.irGetField(getter!!.dispatchReceiverParameter?.let { declarationBuilder.irGet(it) }, observationRegistrarField)
                putValueArgument(0, declarationBuilder.irString(name.identifier))
            }
        )

        // TODO: Replace setter to use `withMutation` (generated by `addWithMutationMethod`)
        println("Setter: $setter")
        val originalSetterBody = setter!!.body!!

        setter!!.body = declarationBuilder.irBlockBody() {
            +declarationBuilder.irTry(
                type = setter!!.returnType,
                tryResult = declarationBuilder.irBlock() {
                    +irCall(bridge.willSetProperty).apply {
                        dispatchReceiver = irGetField(setter!!.dispatchReceiverParameter?.let(::irGet), observationRegistrarField)
                        putValueArgument(0, irString(name.identifier))
                    }
                    originalSetterBody.statements.forEach {
                        +it
                    }
                },
                catches = emptyList(),
                finallyExpression = declarationBuilder.irCall(bridge.didSetProperty).apply {
                    dispatchReceiver = irGetField(setter!!.dispatchReceiverParameter?.let(::irGet), observationRegistrarField)
                    putValueArgument(0, declarationBuilder.irString(name.identifier))
                }
            )
        }
    }
}

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
