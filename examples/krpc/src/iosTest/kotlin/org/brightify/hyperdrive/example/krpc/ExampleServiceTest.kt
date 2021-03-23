package org.brightify.hyperdrive.example.krpc

import kotlinx.coroutines.runBlocking
import org.brightify.hyperdrive.krpc.frame.RPCEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class ExampleServiceTest {

    @Test
    fun serializeEvent() {
        println(RPCEvent.serializersModule)
    }

    @Test
    fun runTest() = runBlocking {
        val client = makeClient()
        assertEquals(5, client.strlen("Hello"))
    }

}