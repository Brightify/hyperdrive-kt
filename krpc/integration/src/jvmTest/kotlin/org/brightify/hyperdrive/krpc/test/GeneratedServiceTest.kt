package org.brightify.hyperdrive.krpc.test

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.datatest.forAll
import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.LoggingLevel
import org.brightify.hyperdrive.client.impl.ProtoBufWebSocketFrameConverter
import org.brightify.hyperdrive.client.impl.ServiceClientImpl
import org.brightify.hyperdrive.client.impl.SingleFrameConverterWrapper
import org.brightify.hyperdrive.client.impl.WebSocketClient
import org.brightify.hyperdrive.krpc.api.RPCFrameDeserializationStrategy
import org.brightify.hyperdrive.krpc.api.RPCFrameSerializationStrategy
import org.brightify.hyperdrive.krpc.api.RPCProtocol
import org.brightify.hyperdrive.krpc.api.RPCTransport
import org.brightify.hyperdrive.krpc.api.ServiceDescriptor
import org.brightify.hyperdrive.krpc.api.error.RPCNotFoundError
import org.brightify.hyperdrive.krpc.api.impl.AscensionRPCProtocol
import org.brightify.hyperdrive.krpc.api.impl.DefaultServiceRegistry
import org.brightify.hyperdrive.krpc.server.impl.KtorServerFrontend
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.toList

class GeneratedServiceTest: BehaviorSpec({
    val serviceImpl = object: BasicTestService {
        override suspend fun multiplyByTwo(source: Int): Int {
            return source * 2
        }

        override suspend fun multiply(source: Int, multiplier: Int): Int {
            return source * multiplier
        }

        override suspend fun singleCallError() {
            throw IllegalArgumentError("source cannot be zero")
        }

        override suspend fun sum(stream: Flow<Int>): Int {
            return stream.fold(0) { accumulator, value -> accumulator + value }
        }

        override suspend fun sumWithInitial(initialValue: Int, stream: Flow<Int>): Int {
            return stream.fold(initialValue) { accumulator, value -> accumulator + value }
        }

        override suspend fun clientStreamError(stream: Flow<Unit>): IllegalArgumentError {
            try {
                stream.collect()
                error("Expected exception not thrown!")
            } catch (e: IllegalArgumentError) {
                return e
            }
        }

        override suspend fun timer(count: Int): Flow<Int> {
            if (count <= 0) {
                return emptyFlow()
            }
            return ticker(1, 0).receiveAsFlow().withIndex().map { it.index }.take(count)
        }

        override suspend fun multiplyEachByTwo(stream: Flow<Int>): Flow<Int> {
            return stream.map { it * 2 }
        }
    }

    val testScope = TestCoroutineScope()

    beforeSpec {
        Logger.setLevel(LoggingLevel.Trace)
    }

    afterContainer {
        testScope.cleanupTestCoroutines()
    }

    val client = lazy {
        val registry = DefaultServiceRegistry()
        registry.register(BasicTestService.Descriptor.describe(serviceImpl))
        val serverFrontend = KtorServerFrontend(
            frameConverter = SingleFrameConverterWrapper.binary(
                ProtoBufWebSocketFrameConverter(
                    outgoingSerializer = RPCFrameSerializationStrategy(),
                    incomingDeserializer = RPCFrameDeserializationStrategy()
                )
            ),
            serviceRegistry = registry,
        )

        val clientTransport = WebSocketClient(
            connectionScope = testScope,
            frameConverter = SingleFrameConverterWrapper.binary(
                ProtoBufWebSocketFrameConverter(
                    outgoingSerializer = RPCFrameSerializationStrategy(),
                    incomingDeserializer = RPCFrameDeserializationStrategy()
                )
            )
        )
        ServiceClientImpl(clientTransport, DefaultServiceRegistry(), testScope)
    }

    val protocol = lazy {
        val registry = DefaultServiceRegistry()
        registry.register(BasicTestService.Descriptor.describe(serviceImpl))
        val connection = LoopbackConnection(testScope)

        AscensionRPCProtocol.Factory(registry).create(connection)
    }

    listOf(protocol, client).forEach { lazyTransport ->
        val transport = lazyTransport.value
        Given("An RPCTransport ${transport::class.simpleName}") {
            val service = BasicTestService.Client(transport) as BasicTestService
            And("Basic Test Service") {
                When("Running single call") {
                    Then("`multiplyByTwo` returns input times two") {
                        checkAll<Int> { input ->
                            service.multiplyByTwo(input) shouldBe input * 2
                        }
                    }

                    Then("`multiply` returns first parameter times second parameter") {
                        checkAll<Int, Int> { lhs, rhs ->
                            service.multiply(lhs, rhs) shouldBe lhs * rhs
                        }
                    }

                    Then("`singleCallError` fails") {
                        shouldThrowExactly<IllegalArgumentError> {
                            service.singleCallError()
                        }
                    }
                }

                When("Running upstream call") {
                    Then("`sum` returns sum of all upstream flow events") {
                        listOf(
                            row(21, flowOf(1, 2, 3, 4, 5, 6)),
                            row(0, emptyFlow<Int>()),
                        ).forEach { (sum, flow) ->
                            service.sum(flow) shouldBe sum
                        }
                    }

                    Then("`sumWithInitial` returns sum of all upstream flow events and an initial value") {
                        listOf(
                            row(30, 9, flowOf(1, 2, 3, 4, 5, 6)),
                            row(0, -9, flowOf(3, 3, 3)),
                            row(3, 2, flowOf(1, 2, 3).take(1)),
                        ).forEach { (sum, initial, flow) ->
                            service.sumWithInitial(initial, flow) shouldBe sum
                        }
                    }

                    Then("`clientStreamError` fails") {
                        shouldThrowExactly<IllegalArgumentError> {
                            service.clientStreamError(flow { throw IllegalArgumentError("Expected error") })
                        }
                    }
                }

                When("Running downstream call") {
                    Then("`timer` returns flow with incrementing elements up to requested count") {
                        listOf(
                            row(6, listOf(0, 1, 2, 3, 4, 5)),
                            row(0, emptyList()),
                            row(1, listOf(0)),
                        ).forEach { (input, expectedResult) ->
                            service.timer(input).toList() shouldContainExactly expectedResult
                        }
                    }
                }
            }
        }
    }

    // // @Test
    // fun `perform generated bistream call`() = runBlocking {
    //     val service = BasicTestService.Client(client)
    //
    //     assertEquals(42, service.multiplyEachByTwo(flowOf(1, 2, 3, 4, 5, 6)).reduce { accumulator, value -> accumulator + value })
    // }
})