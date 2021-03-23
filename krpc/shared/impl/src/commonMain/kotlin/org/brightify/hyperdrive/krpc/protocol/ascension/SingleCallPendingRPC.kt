package org.brightify.hyperdrive.krpc.protocol.ascension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.SerializationStrategy
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.SingleCallDescription
import org.brightify.hyperdrive.krpc.frame.DownstreamRPCEvent
import org.brightify.hyperdrive.krpc.frame.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.frame.RPCFrame
import org.brightify.hyperdrive.krpc.util.RPCReference
import org.brightify.hyperdrive.krpc.frame.UpstreamRPCEvent
import org.brightify.hyperdrive.krpc.error.RPCProtocolViolationError
import org.brightify.hyperdrive.krpc.error.UnknownRPCReferenceException
import org.brightify.hyperdrive.krpc.frame.OutgoingRPCFrame
import org.brightify.hyperdrive.utils.Do

object SingleCallPendingRPC {
    class Server<REQUEST, RESPONSE>(
        connection: RPCConnection,
        reference: RPCReference,
        call: RunnableCallDescription.Single<REQUEST, RESPONSE>,
        onFinished: () -> Unit,
    ): PendingRPC.Server<REQUEST, RunnableCallDescription.Single<REQUEST, RESPONSE>>(connection, reference, call, onFinished) {
        private companion object {
            val logger = Logger<SingleCallPendingRPC.Server<*, *>>()
        }

        override suspend fun handle(frame: IncomingRPCFrame<UpstreamRPCEvent>) {
            Do exhaustive when (frame.header.event) {
                is UpstreamRPCEvent.Open -> launch {
                    val data = frame.decoder.decodeSerializableValue(call.requestSerializer)
                    frame.respond(
                        call.perform(data)
                    )
                }
                UpstreamRPCEvent.Error -> {
                    val error = errorSerializer.decodeThrowable(frame.decoder)
                    if (error is UnknownRPCReferenceException) {
                        cancel("Client sent an UnknownRPCReferenceException which probably means we sent it a frame by mistake.", error)
                    } else {
                        throw RPCProtocolViolationError("SingleCall doesn't accept Error frame.")
                    }
                }
                UpstreamRPCEvent.Warning -> {
                    val error = errorSerializer.decodeThrowable(frame.decoder)
                    logger.warning(error) { "Client sent a warning." }
                }
                UpstreamRPCEvent.Cancel -> {
                    cancel("Client asked to cancel the call.")
                }
                is UpstreamRPCEvent.StreamOperation, UpstreamRPCEvent.Data -> {
                    throw RPCProtocolViolationError("")
                }
            }
        }

        private suspend fun IncomingRPCFrame<UpstreamRPCEvent>.respond(payload: RESPONSE) {
            connection.send(OutgoingRPCFrame(
                RPCFrame.Header(header.callReference, DownstreamRPCEvent.Response),
                call.responseSerializer as SerializationStrategy<Any?>,
                payload as Any?,
            ))
        }
    }

    class Client<REQUEST, RESPONSE>(
        connection: RPCConnection,
        call: SingleCallDescription<REQUEST, RESPONSE>,
        reference: RPCReference,
        onFinished: () -> Unit,
    ): PendingRPC.Client<REQUEST, RESPONSE, SingleCallDescription<REQUEST, RESPONSE>>(connection, reference, call, onFinished) {
        private companion object {
            val logger = Logger<SingleCallPendingRPC.Client<*, *>>()
        }

        private val responseDeferred = CompletableDeferred<RESPONSE>()

        override suspend fun perform(payload: REQUEST): RESPONSE = run {
            open(payload)

            responseDeferred.await()
        }

        override suspend fun handle(frame: IncomingRPCFrame<DownstreamRPCEvent>) {
            Do exhaustive when (frame.header.event) {
                DownstreamRPCEvent.Response -> {
                    responseDeferred.complete(frame.response)
                }
                DownstreamRPCEvent.Warning -> {
                    val error = errorSerializer.decodeThrowable(frame.decoder)
                    logger.warning(error) { "Received a warning from the server." }
                }
                DownstreamRPCEvent.Error -> {
                    val error = errorSerializer.decodeThrowable(frame.decoder)
                    responseDeferred.completeExceptionally(error)
                }
                DownstreamRPCEvent.Data, DownstreamRPCEvent.Opened, is DownstreamRPCEvent.StreamOperation -> {
                    throw RPCProtocolViolationError("SingleCall only accepts Response frame.")
                }
            }
        }

        private val IncomingRPCFrame<DownstreamRPCEvent>.response: RESPONSE
            get() = decoder.decodeSerializableValue(call.incomingSerializer)
    }
}