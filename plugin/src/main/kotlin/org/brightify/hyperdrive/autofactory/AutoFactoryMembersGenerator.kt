package org.brightify.hyperdrive.autofactory

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.primaryConstructorSymbol
import org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createNestedClass
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.constructStarProjectedType
import org.jetbrains.kotlin.javac.resolve.classId
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class AutoFactoryMembersGenerator(session: FirSession): FirDeclarationGenerationExtension(session) {
    companion object {
        private val predicate = LookupPredicate.create {
            annotated(FqName("org.brightify.hyperdrive.AutoFactory"))
        }
        private val factoryName = Name.identifier("Factory")
        private val createName = Name.identifier("create")
        private val provided = classId("org.brightify.hyperdrive", "Provided")
    }

    private val predicateBasedProvider = session.predicateBasedProvider
    private val matchedClasses by lazy {
        predicateBasedProvider.getSymbolsByPredicate(predicate).filterIsInstance<FirRegularClassSymbol>()
    }
    private val nestedClassIds by lazy {
        matchedClasses.map { it.classId.createNestedClassId(factoryName) }
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext,
    ): FirClassLikeSymbol<*>? {
        return if (matchedClasses.none { it == owner }) {
            null
        } else {
            createNestedClass(owner, name, Key).symbol
        }
    }

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        val constructor = createConstructor(context.owner, Key, isPrimary = true) {
            (context.owner.getContainingClassSymbol(session) as FirClassSymbol<*>)
                .primaryConstructorSymbol(session)!!
                .valueParameterSymbols
                .filter {
                    !it.hasAnnotation(provided, session)
                }
                .forEach { parameter ->
                    valueParameter(
                        name = parameter.name,
                        type = parameter.resolvedReturnType,
                    )
                }
        }
        return listOf(
            constructor.symbol
        )
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> {
        if (callableId.callableName != createName) {
            return emptyList()
        }
        if (context == null) {
            return emptyList()
        }
        val matchedClassSymbol = (context.owner.getContainingClassSymbol(session) as FirClassSymbol<*>) ?: return emptyList()

        val function = createMemberFunction(
            context.owner,
            Key,
            callableId.callableName,
            matchedClassSymbol.constructStarProjectedType()
        ) {
            matchedClassSymbol.primaryConstructorSymbol(session)!!
                .valueParameterSymbols
                .filter {
                    it.hasAnnotation(provided, session)
                }
                .forEach { parameter ->
                    valueParameter(
                        name = parameter.name,
                        type = parameter.resolvedReturnType,
                    )
                }
        }

        return listOf(function.symbol)
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> {
        return if (classSymbol in matchedClasses) {
            setOf(factoryName)
        } else {
            emptySet()
        }
    }

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
        return when {
            classSymbol.classId in nestedClassIds -> setOf(
                SpecialNames.INIT,
                createName,
            )
            else -> emptySet()
        }
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(predicate)
    }

    object Key: GeneratedDeclarationKey() {
        override fun toString(): String {
            return "AutoFactoryGeneratorKey"
        }
    }
}
