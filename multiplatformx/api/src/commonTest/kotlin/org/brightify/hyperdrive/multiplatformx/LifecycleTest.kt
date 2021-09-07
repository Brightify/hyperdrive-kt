package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LifecycleTest {

    @Test
    fun test1() {
        val scope = MainScope()

        val parent = LifecycleRoot("parent")
        val child = Lifecycle("child")
        val cancelAttach = parent.attach(scope)
        assertTrue(parent.isAttached)

        parent.addChild(child)
        assertTrue(parent.hasChild(child))
        assertTrue(parent.isAttached)
        assertTrue(child.isAttached)
        println(parent.dumpTree())

        child.removeFromParent()
        assertFalse(parent.hasChild(child))
        assertTrue(parent.isAttached)
        assertFalse(child.isAttached)

        cancelAttach.cancel()
        println(parent.dumpTree())
    }
}