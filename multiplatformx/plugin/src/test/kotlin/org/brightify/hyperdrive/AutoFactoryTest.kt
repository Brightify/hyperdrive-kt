package org.brightify.hyperdrive

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import kotlin.test.Test
import kotlin.test.assertEquals

internal class AutoFactoryTest {

    @Test
    fun testAutoFactory() {
        val serviceSource = SourceFile.kotlin("TestViewModel.kt", """
            import org.brightify.hyperdrive.multiplatformx.AutoFactory
            import org.brightify.hyperdrive.multiplatformx.Provided
            
            @AutoFactory
            class TestViewModel(
                private val a: Int,
                @Provided
                private val b: String
            )
        """.trimIndent())


        val usage = SourceFile.kotlin("Usage.kt", """
            fun x() {
                val f = TestViewModel.Factory(10)
                f.create("hello")
            }
        """.trimIndent())

        val result = KotlinCompilation().apply {
            sources = listOf(serviceSource, usage)

            compilerPlugins = listOf<ComponentRegistrar>(
                MultiplatformXComponentRegistrar()
            )

            useIR = true

            includeRuntime = true
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        println(result.generatedFiles)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

//
//        val secondResult = KotlinCompilation().apply {
//            sources = listOf(usage)
//
//            useIR = true
//            includeRuntime = true
//            inheritClassPath = true
//            messageOutputStream = System.out
//
//            this.cla
//        }

        val factoryClass = result.classLoader.loadClass("TestViewModel\$Factory")
        val ctor = factoryClass.getDeclaredConstructor(Int::class.java)
        val factory = ctor.newInstance(10)
        val create = factoryClass.getDeclaredMethod("create", String::class.java)
        val createdInstance = create.invoke(factory, "Hello")
        println(createdInstance)
    }

}