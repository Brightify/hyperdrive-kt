package org.brightify.hyperdrive.krpc

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.protobuf.ProtoBuf
import org.brightify.hyperdrive.krpc.application.RPCExtension
import org.brightify.hyperdrive.krpc.description.CallDescription
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.description.ServiceDescription
import org.brightify.hyperdrive.krpc.description.ServiceDescriptor
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.error.RPCErrorSerializer
import org.brightify.hyperdrive.krpc.protocol.RPCIncomingInterceptor
import org.brightify.hyperdrive.krpc.protocol.RPCOutgoingInterceptor
import org.brightify.hyperdrive.krpc.protocol.RPCProtocol
import org.brightify.hyperdrive.krpc.protocol.ascension.PayloadSerializer
import org.brightify.hyperdrive.krpc.session.ContextKeyRegistry
import org.brightify.hyperdrive.krpc.session.Session
import org.brightify.hyperdrive.utils.Do
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass

class SessionNodeExtension(
    private val contextKeyRegistry: ContextKeyRegistry,
    private val payloadSerializer: PayloadSerializer,
): Session, ContextUpdateService, RPCExtension {
    class Factory(
        private val contextKeyRegistry: ContextKeyRegistry,
        private val payloadSerializer: PayloadSerializer,
    ): RPCExtension.Factory {
        override val identifier = RPCExtension.Identifier("builtin:Session")
        override fun create(): RPCExtension {
            return SessionNodeExtension(contextKeyRegistry, payloadSerializer)
        }
    }

    override val key: CoroutineContext.Key<*> get() = Session

    override val providedServices: List<ServiceDescription> = listOf(
        ContextUpdateService.Descriptor.describe(this)
    )

    private val context = Session.Context(mutableMapOf())
    private val contextModificationLock = Mutex()
    private var runningContextUpdate: Job? = null

    private lateinit var client: ContextUpdateService.Client

    override suspend fun bind(transport: RPCTransport) {
        client = ContextUpdateService.Client(transport)
    }

    override suspend fun update(request: ContextUpdateRequest): ContextUpdateResult = contextModificationLock.withLock {
        val modificationsWithKeys = request.modifications.mapKeys { getKeyOrUnsupported(it.key) }
        val rejectedItems = modificationsWithKeys.filter { (key, modification) ->
            modification.oldRevisionOrNull != context[key]?.revision
        }

        if (rejectedItems.isEmpty()) {
            for ((key, modification) in modificationsWithKeys) {
                when (modification) {
                    // No action is needed.
                    is ContextUpdateRequest.Modification.Required -> continue
                    is ContextUpdateRequest.Modification.Set -> {
                        deserializeAndPut(key, modification.newItem)
                    }
                    is ContextUpdateRequest.Modification.Remove -> {
                        context.remove(key)
                    }
                }
            }

            ContextUpdateResult.Accepted
        } else {
            ContextUpdateResult.Rejected(
                rejectedItems.mapValues { (key, _) ->
                    context[key]?.let {
                        ContextUpdateResult.Rejected.Reason.Updated(it.toDto())
                    } ?: ContextUpdateResult.Rejected.Reason.Removed
                }.mapKeys { it.key.qualifiedName }
            )
        }
    }
    private fun getKeyOrUnsupported(qualifiedName: String): Session.Context.Key<*> {
        return contextKeyRegistry.getKeyByQualifiedName(qualifiedName) ?: UnsupportedKey(qualifiedName)
    }

    private fun <T: Any> deserializeAndPut(key: Session.Context.Key<T>, item: ContextItemDto) {
        val value = if (key is UnsupportedKey) {
            item.value as T
        } else {
            payloadSerializer.deserialize(key.serializer, item.value)
        }
        context.put(Session.Context.Item(key, item.revision, value), key)
    }

    private fun <T: Any> Session.Context.Item<T>.toDto(): ContextItemDto {
        val serializedValue = if (key is UnsupportedKey) {
            value as SerializedPayload
        } else {
            payloadSerializer.serialize(key.serializer, value)
        }
        return ContextItemDto(revision, serializedValue)
    }

    override suspend fun contextTransaction(block: Session.Context.Mutator.() -> Unit) {
        val ourJob = Job()

        // Before running the transaction, we want to make sure there's no other context sync running.
        awaitCompletedContextSync()
        runningContextUpdate = ourJob

        // TODO: Add timeout / number of retries.
        do {
            val modifications = mutableMapOf<Session.Context.Key<Any>, Session.Context.Mutator.Action>()
            val mutator = Session.Context.Mutator(context, modifications)

            block(mutator)

            val request = ContextUpdateRequest(
                modifications.mapValues { (key, action) ->
                    when (action) {
                        is Session.Context.Mutator.Action.Required -> ContextUpdateRequest.Modification.Required(action.oldItem?.revision)
                        is Session.Context.Mutator.Action.Set -> ContextUpdateRequest.Modification.Set(
                            action.oldItem?.revision,
                            action.newItem.toDto()
                        )
                        is Session.Context.Mutator.Action.Remove -> ContextUpdateRequest.Modification.Remove(action.oldItem.revision)
                    }
                }.mapKeys { it.key.qualifiedName }
            )
            val result = client.update(request)

            contextModificationLock.withLock {
                when (result) {
                    is ContextUpdateResult.Rejected -> {
                        val modificationsWithKeys = result.rejectedModifications.mapKeys { getKeyOrUnsupported(it.key) }
                        for ((key, reason) in modificationsWithKeys) {
                            Do exhaustive when (reason) {
                                ContextUpdateResult.Rejected.Reason.Removed -> context.remove(key)
                                is ContextUpdateResult.Rejected.Reason.Updated -> {
                                    deserializeAndPut(key, reason.newItem)
                                }
                            }
                        }
                    }
                    ContextUpdateResult.Accepted -> {
                        for ((key, action) in modifications) {
                            Do exhaustive when (action) {
                                is Session.Context.Mutator.Action.Required -> continue
                                is Session.Context.Mutator.Action.Set -> context.put(action.newItem as Session.Context.Item<Any>, key)
                                is Session.Context.Mutator.Action.Remove -> context.remove(key)
                            }
                        }
                    }
                }
            }
        } while (result != ContextUpdateResult.Accepted)

        runningContextUpdate = null
        ourJob.complete()
    }

    override suspend fun <PAYLOAD, RESPONSE> interceptIncomingSingleCall(
        payload: PAYLOAD,
        call: RunnableCallDescription.Single<PAYLOAD, RESPONSE>,
        next: suspend (PAYLOAD) -> RESPONSE,
    ): RESPONSE = withSessionIfNeeded(call) {
        super.interceptIncomingSingleCall(payload, call, next)
    }

    override suspend fun <PAYLOAD, CLIENT_STREAM, RESPONSE> interceptIncomingUpstreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: RunnableCallDescription.ColdUpstream<PAYLOAD, CLIENT_STREAM, RESPONSE>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> RESPONSE,
    ): RESPONSE = withSessionIfNeeded(call) {
        super.interceptIncomingUpstreamCall(payload, stream, call, next)
    }

    override suspend fun <PAYLOAD, SERVER_STREAM> interceptIncomingDownstreamCall(
        payload: PAYLOAD,
        call: RunnableCallDescription.ColdDownstream<PAYLOAD, SERVER_STREAM>,
        next: suspend (PAYLOAD) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> = withSessionIfNeeded(call) {
        super.interceptIncomingDownstreamCall(payload, call, next)
    }

    override suspend fun <PAYLOAD, CLIENT_STREAM, SERVER_STREAM> interceptIncomingBistreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: RunnableCallDescription.ColdBistream<PAYLOAD, CLIENT_STREAM, SERVER_STREAM>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> = withSessionIfNeeded(call) {
        super.interceptIncomingBistreamCall(payload, stream, call, next)
    }

    @OptIn(ExperimentalContracts::class)
    private suspend fun <RESULT> withSessionIfNeeded(call: RunnableCallDescription<*>, block: suspend () -> RESULT): RESULT {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        // We don't want to inject the service into the coroutine context if it's the CoroutineSyncService's call.
        return if (call.identifier.serviceId == ContextUpdateService.Descriptor.identifier) {
            block()
        } else {
            // TODO: Replace `this` with `immutableCopy`
            withContext(coroutineContext + this) {
                val result = block()
                awaitCompletedContextSync()
                result
            }
        }
    }

    override suspend fun <PAYLOAD, RESPONSE> interceptOutgoingSingleCall(
        payload: PAYLOAD,
        call: SingleCallDescription<PAYLOAD, RESPONSE>,
        next: suspend (PAYLOAD) -> RESPONSE,
    ): RESPONSE = withCompletedContextSyncIfNeeded(call) {
        super.interceptOutgoingSingleCall(payload, call, next)
    }

    override suspend fun <PAYLOAD, CLIENT_STREAM, RESPONSE> interceptOutgoingUpstreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: ColdUpstreamCallDescription<PAYLOAD, CLIENT_STREAM, RESPONSE>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> RESPONSE,
    ): RESPONSE = withCompletedContextSyncIfNeeded(call) {
        super.interceptOutgoingUpstreamCall(payload, stream, call, next)
    }

    override suspend fun <PAYLOAD, SERVER_STREAM> interceptOutgoingDownstreamCall(
        payload: PAYLOAD,
        call: ColdDownstreamCallDescription<PAYLOAD, SERVER_STREAM>,
        next: suspend (PAYLOAD) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> = withCompletedContextSyncIfNeeded(call) {
        super.interceptOutgoingDownstreamCall(payload, call, next)
    }

    override suspend fun <PAYLOAD, CLIENT_STREAM, SERVER_STREAM> interceptOutgoingBistreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: ColdBistreamCallDescription<PAYLOAD, CLIENT_STREAM, SERVER_STREAM>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> = withCompletedContextSyncIfNeeded(call) {
        super.interceptOutgoingBistreamCall(payload, stream, call, next)
    }

    private suspend fun <RESULT> withCompletedContextSyncIfNeeded(call: CallDescription<*>, block: suspend () -> RESULT): RESULT {
        // We can't wait for the context to sync if it's the ContextSyncService's call.
        return if (call.identifier.serviceId == ContextUpdateService.Descriptor.identifier) {
            block()
        } else {
            awaitCompletedContextSync()
            block()
        }
    }

    private suspend fun awaitCompletedContextSync() {
        runningContextUpdate?.join()
    }
}

class RPCContribution<T: Any>(
    val contribution: T,
    contributionClass: KClass<T>,
): CoroutineContext.Element {
    data class Key<T: Any>(val contributionClass: KClass<T>): CoroutineContext.Key<RPCContribution<T>>

    override val key: CoroutineContext.Key<*> = Key(contributionClass)
}

suspend fun <RESULT> withContributed(module: SerializersModule, block: suspend () -> RESULT): RESULT {
    return withContribution(module, SerializersModule::class, block)
}

internal fun <T: Any> CoroutineContext.contribution(contributionClass: KClass<T>): T? {
    return get(RPCContribution.Key(contributionClass))?.contribution
}

internal suspend inline fun <reified T: Any> contextContribution(): T? {
    return coroutineContext.contribution(T::class)
}

internal suspend fun <T: Any, RESULT> withContribution(contribution: T, contributionClass: KClass<T>, block: suspend () -> RESULT): RESULT {
    return withContext(coroutineContext + RPCContribution(contribution, contributionClass)) {
        block()
    }
}

@Serializable
class ContextItemDto(
    val revision: Int,
    val value: SerializedPayload,
)

typealias KeyDto = String

@Serializable
class ContextUpdateRequest(
    val modifications: Map<KeyDto, Modification>
) {
    @Serializable
    sealed class Modification {
        abstract val oldRevisionOrNull: Int?

        @Serializable
        class Required(val oldRevision: Int?): Modification() {
            override val oldRevisionOrNull: Int?
                get() = oldRevision
        }

        @Serializable
        class Set(val oldRevision: Int?, val newItem: ContextItemDto): Modification() {
            override val oldRevisionOrNull: Int?
                get() = oldRevision
        }
        @Serializable
        class Remove(val oldRevision: Int): Modification() {
            override val oldRevisionOrNull: Int?
                get() = oldRevision
        }
    }
}

@Serializable
sealed class ContextUpdateResult {
    @Serializable
    object Accepted: ContextUpdateResult()
    @Serializable
    class Rejected(
        val rejectedModifications: Map<KeyDto, Reason>
    ): ContextUpdateResult() {
        @Serializable
        sealed class Reason {
            @Serializable
            object Removed: Reason()
            @Serializable
            class Updated(val newItem: ContextItemDto): Reason()
        }
    }
}

interface ContextUpdateService {
    suspend fun update(request: ContextUpdateRequest): ContextUpdateResult

    class Client(
        private val transport: RPCTransport,
    ): ContextUpdateService {
        override suspend fun update(request: ContextUpdateRequest): ContextUpdateResult {
            return transport.singleCall(Descriptor.Call.update, request)
        }
    }

    object Descriptor: ServiceDescriptor<ContextUpdateService> {
        const val identifier = "builtin:hyperdrive.ContextSyncService"

        override fun describe(service: ContextUpdateService): ServiceDescription {
            return ServiceDescription(
                identifier,
                listOf(
                    Call.update.calling { request ->
                        service.update(request)
                    }
                )
            )
        }

        object Call {
            val update = SingleCallDescription(
                ServiceCallIdentifier(identifier, "update"),
                ContextUpdateRequest.serializer(),
                ContextUpdateResult.serializer(),
                RPCErrorSerializer(),
            )
        }
    }
}

class UnsupportedKey(override val qualifiedName: String): Session.Context.Key<SerializedPayload> {
    override val serializer: KSerializer<SerializedPayload> = SerializedPayload.serializer()
}
