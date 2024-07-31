package org.brightify.hyperdrive.autofactory

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptorWithResolutionScopes
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.DescriptorVisibility
import org.jetbrains.kotlin.descriptors.InlineClassRepresentation
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.SupertypeLoopChecker
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueClassRepresentation
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ClassDescriptorBase
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtContextReceiver
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPureClassOrObject
import org.jetbrains.kotlin.psi.KtPureElement
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.allConstructors
import org.jetbrains.kotlin.psi.synthetics.SyntheticClassOrObjectDescriptor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorFactory
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.descriptorUtil.secondaryConstructors
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.data.KtClassLikeInfo
import org.jetbrains.kotlin.resolve.lazy.data.KtClassOrObjectInfo
import org.jetbrains.kotlin.resolve.lazy.data.KtScriptInfo
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlin.resolve.lazy.descriptors.ClassResolutionScopesSupport
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassMemberScope
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.AbstractClassTypeConstructor
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.checker.KotlinTypeRefiner

val KtPureClassOrObject.isAnyConstructorAutoFactoryAnnotated: Boolean
    get() = primaryConstructor?.let { it.annotationEntries.any { it.shortName == AutoFactoryNames.Annotation.autoFactory.shortName() } } ?: false ||
        secondaryConstructors.any { it.annotationEntries.any { it.shortName == AutoFactoryNames.Annotation.autoFactory.shortName() } }

val ClassDescriptor.autoFactoryConstructor: ConstructorDescriptor?
    get() = constructors.firstOrNull { it.annotations.hasAnnotation(AutoFactoryNames.Annotation.autoFactory) } ?:
        if (annotations.hasAnnotation(AutoFactoryNames.Annotation.autoFactory)) {
            unsubstitutedPrimaryConstructor
        } else {
            null
        }

val IrClass.parentAutoFactoryConstructor: IrConstructor?
    get() = when (val parent = parent) {
        is IrClass -> {
            parent.constructors.firstOrNull { it.annotations.hasAnnotation(AutoFactoryNames.Annotation.autoFactory) } ?:
            if (parent.hasAnnotation(AutoFactoryNames.Annotation.autoFactory)) {
                parent.primaryConstructor
            } else {
                null
            }
        }
        else -> null
    }

val ConstructorDescriptor.injectedValueParameters: List<ValueParameterDescriptor>
    get() = valueParameters.filterNot { it.annotations.hasAnnotation(AutoFactoryNames.Annotation.provided) }

val ConstructorDescriptor.providedValueParameters: List<ValueParameterDescriptor>
    get() = valueParameters.filter { it.annotations.hasAnnotation(AutoFactoryNames.Annotation.provided) }

open class AutoFactoryResolveExtension: SyntheticResolveExtension {
    override fun getSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name> = if (
        thisDescriptor.annotations.hasAnnotation(AutoFactoryNames.Annotation.autoFactory) ||
        (thisDescriptor.source.getPsi() as? KtPureClassOrObject)?.isAnyConstructorAutoFactoryAnnotated == true) {
        listOf(AutoFactoryNames.factory)
    } else {
        emptyList()
    }

    override fun getPossibleSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name>? {
        return listOf(AutoFactoryNames.factory)
    }

    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> = if (thisDescriptor.name == AutoFactoryNames.factory) {
        listOf(AutoFactoryNames.createFun)
    } else {
        emptyList()
    }

    override fun generateSyntheticClasses(
        thisDescriptor: ClassDescriptor,
        name: Name,
        ctx: LazyClassContext,
        declarationProvider: ClassMemberDeclarationProvider,
        result: MutableSet<ClassDescriptor>
    ) {
        if (name != AutoFactoryNames.factory) { return }

        val thisDeclaration = declarationProvider.correspondingClassOrObject ?: return
        if (
            thisDescriptor.annotations.hasAnnotation(AutoFactoryNames.Annotation.autoFactory) ||
            thisDeclaration.isAnyConstructorAutoFactoryAnnotated
        ) {
            val scope = ctx.declarationScopeProvider.getResolutionScopeForDeclaration(declarationProvider.ownerInfo?.scopeAnchor ?:  return)

            val factoryDescriptor = FactoryClassDescriptor(
                ctx,
                thisDeclaration,
                thisDescriptor,
                name,
                thisDescriptor.source,
                scope,
            )

            result.add(factoryDescriptor)
        }
    }

    override fun addSyntheticSupertypes(thisDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {
        super.addSyntheticSupertypes(thisDescriptor, supertypes)

        if (thisDescriptor.name == AutoFactoryNames.factory) {
            supertypes.add(thisDescriptor.module.builtIns.anyType)
        }
    }

    override fun generateSyntheticMethods(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: List<SimpleFunctionDescriptor>,
        result: MutableCollection<SimpleFunctionDescriptor>
    ) {
        super.generateSyntheticMethods(thisDescriptor, name, bindingContext, fromSupertypes, result)

        if (name != AutoFactoryNames.createFun) { return }

        val containingClass = thisDescriptor.containingDeclaration as? ClassDescriptor ?: return
        val autoFactoryConstructor = containingClass.autoFactoryConstructor ?: return

        val createFunDescriptor = SimpleFunctionDescriptorImpl.create(
            thisDescriptor,
            Annotations.EMPTY,
            name,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            thisDescriptor.source
        )

        createFunDescriptor.initialize(
            null,
            thisDescriptor.thisAsReceiverParameter,
            emptyList(),
            autoFactoryConstructor.providedValueParameters
                .mapIndexed { index, parameter ->
                    ValueParameterDescriptorImpl(
                        createFunDescriptor,
                        null,
                        index,
                        Annotations.EMPTY,
                        parameter.name,
                        parameter.type,
                        declaresDefaultValue = false,
                        isCrossinline = false,
                        isNoinline = false,
                        varargElementType = null,
                        source = createFunDescriptor.source
                    )
                },
            containingClass.defaultType,
            Modality.FINAL,
            DescriptorVisibilities.PUBLIC
        )

        result.add(createFunDescriptor)
    }

    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name? {
        return null
    }

    override fun getSyntheticPropertiesNames(thisDescriptor: ClassDescriptor): List<Name> {
        return emptyList()
    }
}

val AUTO_FACTORY_PLUGIN_ORIGIN = IrDeclarationOriginImpl("AUTO_FACTORY", true)

class FactoryClassDescriptor(
    context: LazyClassContext,
    parentClassOrObject: KtPureClassOrObject,
    private val autoFactoryParentDeclaration: ClassDescriptor,
    name: Name,
    source: SourceElement,
    outerScope: LexicalScope,
): ClassDescriptorBase(context.storageManager, autoFactoryParentDeclaration, name, source, false), ClassDescriptorWithResolutionScopes {
    val syntheticDeclaration: KtPureClassOrObject = SyntheticDeclaration(parentClassOrObject, name.asString())
    private val thisDescriptor: FactoryClassDescriptor get() = this // code readability

    private val typeConstructor = SyntheticTypeConstructor(context.storageManager)
    private val resolutionScopesSupport = ClassResolutionScopesSupport(thisDescriptor, context.storageManager, context.languageVersionSettings, { outerScope })
    private val syntheticSupertypes = context.storageManager.createLazyValue {
        mutableListOf<KotlinType>().apply {
            context.syntheticResolveExtension.addSyntheticSupertypes(thisDescriptor, this)
        }
    }
    private val unsubstitutedMemberScope = LazyClassMemberScope(context, SyntheticClassMemberDeclarationProvider(syntheticDeclaration), this, context.trace)

    private lateinit var constructorReference: ClassConstructorDescriptor
    private val _unsubstitutedPrimaryConstructor = context.storageManager.createLazyValue({
        createUnsubstitutedPrimaryConstructor(autoFactoryParentDeclaration.visibility)
    }, onRecursiveCall = { _ ->
        constructorReference
    })

    private fun createUnsubstitutedPrimaryConstructor(constructorVisibility: DescriptorVisibility): ClassConstructorDescriptor {
        return ClassConstructorDescriptorImpl.create(
            thisDescriptor,
            Annotations.EMPTY,
            true,
            source
        )
        .also {
            constructorReference = it
        }
        .apply {
            val injectedValueParameters = autoFactoryParentDeclaration.autoFactoryConstructor?.injectedValueParameters ?: emptyList()
            initialize(
                injectedValueParameters
                    .mapIndexed { index, parameter ->
                        ValueParameterDescriptorImpl(
                            this,
                            null,
                            index,
                            Annotations.EMPTY,
                            parameter.name,
                            parameter.type,
                            declaresDefaultValue = false,
                            isCrossinline = false,
                            isNoinline = false,
                            varargElementType = null,
                            source = parameter.source
                        )
                    },
                constructorVisibility,
            )
            this.returnType = getDefaultType()
        }
    }

    override fun toString(): String = "AutoFactory class " + name.toString() + " in " + containingDeclaration

    override fun getUnsubstitutedMemberScope(kotlinTypeRefiner: KotlinTypeRefiner): MemberScope = unsubstitutedMemberScope
    override fun getTypeConstructor(): TypeConstructor = typeConstructor
    override fun getVisibility(): DescriptorVisibility = autoFactoryParentDeclaration.visibility
    override fun getDeclaredTypeParameters(): List<TypeParameterDescriptor> = autoFactoryParentDeclaration.declaredTypeParameters
    override fun getConstructors(): Collection<ClassConstructorDescriptor> = listOf(_unsubstitutedPrimaryConstructor())
    override fun getUnsubstitutedPrimaryConstructor(): ClassConstructorDescriptor? = _unsubstitutedPrimaryConstructor()

    override val annotations: Annotations = Annotations.EMPTY
    override fun getCompanionObjectDescriptor(): ClassDescriptorWithResolutionScopes? = null
    override fun getModality(): Modality = Modality.FINAL
    override fun isExpect(): Boolean = false
    override fun isActual(): Boolean = false
    override fun isInner(): Boolean = false
    override fun getKind(): ClassKind = ClassKind.CLASS
    override fun isCompanionObject(): Boolean = false
    override fun isData(): Boolean = false
    override fun isInline(): Boolean = false
    override fun isFun(): Boolean = false
    override fun isValue(): Boolean = false
    override fun getStaticScope(): MemberScope = MemberScope.Empty
    override fun getSealedSubclasses(): Collection<ClassDescriptor> = emptyList()
    override fun getValueClassRepresentation(): ValueClassRepresentation<SimpleType>? = null
    
    override fun getScopeForClassHeaderResolution(): LexicalScope = resolutionScopesSupport.scopeForClassHeaderResolution()
    override fun getScopeForConstructorHeaderResolution(): LexicalScope = resolutionScopesSupport.scopeForConstructorHeaderResolution()
    override fun getScopeForCompanionObjectHeaderResolution(): LexicalScope = resolutionScopesSupport.scopeForCompanionObjectHeaderResolution()
    override fun getScopeForMemberDeclarationResolution(): LexicalScope = resolutionScopesSupport.scopeForMemberDeclarationResolution()
    override fun getScopeForStaticMemberDeclarationResolution(): LexicalScope = resolutionScopesSupport.scopeForStaticMemberDeclarationResolution()
    override fun getScopeForInitializerResolution(): LexicalScope = throw UnsupportedOperationException("Not supported for synthetic autofactory class.")
    override fun getDeclaredCallableMembers(): Collection<CallableMemberDescriptor> =
        DescriptorUtils.getAllDescriptors(unsubstitutedMemberScope).filterIsInstance<CallableMemberDescriptor>().filter {
            it.kind != CallableMemberDescriptor.Kind.FAKE_OVERRIDE
        }

    private inner class SyntheticTypeConstructor(storageManager: StorageManager): AbstractClassTypeConstructor(storageManager) {
        override val supertypeLoopChecker: SupertypeLoopChecker = SupertypeLoopChecker.EMPTY
        override fun computeSupertypes(): Collection<KotlinType> = syntheticSupertypes()
        override fun getDeclarationDescriptor(): ClassDescriptor = thisDescriptor
        override fun getParameters(): List<TypeParameterDescriptor> = autoFactoryParentDeclaration.declaredTypeParameters
        override fun isDenotable(): Boolean = true
    }

    private class SyntheticClassMemberDeclarationProvider(
        override val correspondingClassOrObject: KtPureClassOrObject
    ) : ClassMemberDeclarationProvider {
        override val ownerInfo: KtClassLikeInfo? = null
        override fun getDeclarations(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean): List<KtDeclaration> = emptyList()
        override fun getFunctionDeclarations(name: Name): Collection<KtNamedFunction> = emptyList()
        override fun getPropertyDeclarations(name: Name): Collection<KtProperty> = emptyList()
        override fun getDestructuringDeclarationsEntries(name: Name): Collection<KtDestructuringDeclarationEntry> = emptyList()
        override fun getClassOrObjectDeclarations(name: Name): Collection<KtClassOrObjectInfo<*>> = emptyList()
        override fun getScriptDeclarations(name: Name): Collection<KtScriptInfo> = emptyList()
        override fun getTypeAliasDeclarations(name: Name): Collection<KtTypeAlias> = emptyList()
        override fun getDeclarationNames() = emptySet<Name>()
    }

    internal inner class SyntheticDeclaration(
        private val _parent: KtPureElement,
        private val _name: String
    ) : KtPureClassOrObject {
        fun descriptor() = thisDescriptor

        override fun getName(): String? = _name
        override fun isLocal(): Boolean = false

        override fun getContextReceivers(): List<KtContextReceiver> = emptyList()

        override fun getDeclarations(): List<KtDeclaration> = emptyList()
        override fun getSuperTypeListEntries(): List<KtSuperTypeListEntry> = emptyList()
        override fun getCompanionObjects(): List<KtObjectDeclaration> = emptyList()

        override fun hasExplicitPrimaryConstructor(): Boolean = false
        override fun hasPrimaryConstructor(): Boolean = false
        override fun getPrimaryConstructor(): KtPrimaryConstructor? = null
        override fun getPrimaryConstructorModifierList(): KtModifierList? = null
        override fun getPrimaryConstructorParameters(): List<KtParameter> = emptyList()
        override fun getSecondaryConstructors(): List<KtSecondaryConstructor> = emptyList()

        override fun getPsiOrParent() = _parent.psiOrParent
        override fun getParent() = _parent.psiOrParent
        @Suppress("USELESS_ELVIS")
        override fun getContainingKtFile() =
            // in theory `containingKtFile` is `@NotNull` but in practice EA-114080
            _parent.containingKtFile ?: throw IllegalStateException("containingKtFile was null for $_parent of ${_parent.javaClass}")

        override fun getBody(): KtClassBody? = null
    }
}
