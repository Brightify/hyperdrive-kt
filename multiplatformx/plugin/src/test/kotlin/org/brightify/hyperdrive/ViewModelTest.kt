package org.brightify.hyperdrive

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ViewModelTest {

    @Test
    fun testViewModel() {
        val serviceSource = SourceFile.kotlin("TestViewModel.kt", """
            import org.brightify.hyperdrive.multiplatformx.ViewModel
            import org.brightify.hyperdrive.multiplatformx.BaseViewModel
            import kotlinx.coroutines.flow.StateFlow
            import org.brightify.hyperdrive.multiplatformx.ManageableViewModel
            import org.brightify.hyperdrive.multiplatformx.property.map
               
            @ViewModel
            class TestViewModel: BaseViewModel() {
                var name: String by published("hello")
                val _observeName by observe(::name)
                val mappedName by observeName.map { it.count() }
            
                fun test() {
                    println(name)
                    println(observeName.value)
                    // println(observeMappedName.value)
                }
            }
        """.trimIndent())


        val result = KotlinCompilation().apply {
            sources = listOf(serviceSource)

            compilerPlugins = listOf<ComponentRegistrar>(
                MultiplatformXComponentRegistrar()
            )

            commandLineProcessors = listOf(
                MultiplatformxCommandLineProcessor()
            )

            pluginOptions = listOf(
                PluginOption(MultiplatformxCommandLineProcessor.pluginId, MultiplatformxCommandLineProcessor.Options.enabled.optionName, "true"),
                PluginOption(MultiplatformxCommandLineProcessor.pluginId, MultiplatformxCommandLineProcessor.Options.viewModelEnabled.optionName, "true")
            )

            useIR = true

            includeRuntime = true
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        println(result.generatedFiles)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val viewModelClass = result.classLoader.loadClass("TestViewModel")
        val ctor = viewModelClass.getDeclaredConstructor()
        val viewModel = ctor.newInstance()
        println(viewModel)
        viewModelClass.methods.single { it.name == "test" }.invoke(viewModel)
    }

}