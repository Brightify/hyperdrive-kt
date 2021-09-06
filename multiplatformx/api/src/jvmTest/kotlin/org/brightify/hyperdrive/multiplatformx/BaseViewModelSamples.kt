package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.withTestContext
import kotlin.test.Test

class BaseViewModelSamples {

    @OptIn(ExperimentalCoroutinesApi::class)
    val testScope = TestCoroutineScope()

    @Test
    fun managedTest() = runBlocking {
        class Child: BaseViewModel()

        class Parent: BaseViewModel() {
            val child by managed(Child())
        }
        val root = LifecycleRoot("root")
        val cancelAttach = root.attach(testScope)
        val parent = Parent()


        root.addChild(parent.lifecycle)
        delay(1000)
        parent.lifecycle.removeFromParent()
        delay(1000)
        root.addChild(parent.lifecycle)
        delay(1000)
        cancelAttach.cancel()
        delay(1000)
        parent.lifecycle.removeFromParent()
        delay(1000)
        root.addChild(parent.lifecycle)
        delay(1000)
        root.attach(testScope)
    }

    @Test
    fun managedListTest() = runBlocking {
        class Child: BaseViewModel()

        class Parent: BaseViewModel() {
            val child by managedList(listOf(Child()))
        }

        val root = LifecycleRoot("root")
        val cancelAttach = root.attach(testScope)

        val parent = Parent()

        root.addChild(parent.lifecycle)
        delay(1000)
        parent.lifecycle.removeFromParent()
        delay(1000)
        root.addChild(parent.lifecycle)
        delay(1000)
        cancelAttach.cancel()
        delay(1000)
        root.attach(testScope)
        delay(1000)
    }

    @Test
    fun notifyObjectWillChange() {
        class Sample: BaseViewModel() {
            var name: String? = null
                private set

            fun rename(newName: String) {
                notifyObjectWillChange()

                name = newName
            }
        }
    }
}
