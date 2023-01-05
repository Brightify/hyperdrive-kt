package org.brightify.hyperdrive.viewmodel

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.ir.psiElement
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageUtil
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name

class ComposeViewIrGenerator(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector,
    private val types: Types,
): IrElementTransformerVoid(), BodyLoweringPass {
    class Types(
        val state: IrClassSymbol,
        val stateValue: IrFunctionSymbol,
        val manageableViewModel: IrClassSymbol,
        val observeAsState: IrFunctionSymbol,
    )

    /**
     * 1. Add `@Composable` annotation resolution
     * 2. For each parameter where `parameter is ManageableViewModel`:
     *      2a. generate val _parameter by parameter.observeAsState()
     *      2b. replace each usage of parameter with `_parameter`
     */
    override fun lower(irBody: IrBody, container: IrDeclaration) {
        // We're only interested in compose functions.
        if (container !is IrFunction) { return }
        // Only process functions with the `@Composable` annotation.
        if (!container.hasAnnotation(ViewModelNames.Compose.composable)) { return }
        // Disable processing of this function if annotated with `@NoAutoObserve`.
        if (container.hasAnnotation(ViewModelNames.Annotation.noAutoObserve)) { return }

        val declarationBuilder = DeclarationIrBuilder(pluginContext, container.symbol)

        val observedBackingVariables = container.valueParameters
            .filter {
                it.type.isSubtypeOfClass(types.manageableViewModel)
            }
            .associateWith { parameter ->
                val stateType = types.state.typeWith(parameter.type)
                IrVariableImpl(
                    startOffset = UNDEFINED_OFFSET,
                    endOffset = UNDEFINED_OFFSET,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                    name = Name.identifier("state$" + parameter.name.identifier),
                    symbol = IrVariableSymbolImpl(),
                    type = stateType,
                    isVar = false,
                    isConst = false,
                    isLateinit = false,
                ).also {
                    it.parent = container
                    it.initializer = declarationBuilder.irCall(types.observeAsState, it.type, listOf(parameter.type)).apply {
                        extensionReceiver = declarationBuilder.irGet(parameter)
                    }
                }
            }

        irBody.transformChildrenVoid(object: IrElementTransformerVoid() {
            override fun visitValueAccess(expression: IrValueAccessExpression): IrExpression {
                val delegate = observedBackingVariables[expression.symbol.owner] ?: return super.visitValueAccess(expression)
                return declarationBuilder.irGet(expression.type, declarationBuilder.irGet(delegate), types.stateValue)
            }
        })

        container.body = when (irBody) {
            is IrBlockBody -> {
                irBody.statements.addAll(0, observedBackingVariables.values)
                irBody
            }
            is IrExpressionBody -> {
                declarationBuilder.irBlockBody {
                    observedBackingVariables.values.forEach {
                        +it
                    }
                    +irBody.expression
                }
            }
            else -> {
                messageCollector.report(
                    CompilerMessageSeverity.WARNING,
                    "Unexpected irBody type: $irBody. Annotate this function with @NoAutoObserve to get rid of this warning.",
                    MessageUtil.psiElementToMessageLocation(container.psiElement))
                return
            }
        }

        messageCollector.report(CompilerMessageSeverity.LOGGING, container.dumpKotlinLike())
    }
}