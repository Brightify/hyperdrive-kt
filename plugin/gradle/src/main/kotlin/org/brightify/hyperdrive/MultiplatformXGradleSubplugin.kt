package org.brightify.hyperdrive

import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class MultiplatformXGradleSubplugin: KotlinCompilerPluginSupportPlugin {
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    // TODO: Apply required dependencies?
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider {
            val hyperdrive = project.extensions.findByType() ?: HyperdriveExtension()
            val multiplatformX = hyperdrive.multiplatformx
            if (multiplatformX != null) {
                listOf(
                    MultiplatformxCommandLineProcessor.Options.enabled.subpluginOption("true"),
                    MultiplatformxCommandLineProcessor.Options.autoFactoryEnabled.subpluginOption(multiplatformX.isAutoFactoryEnabled),
                    MultiplatformxCommandLineProcessor.Options.viewModelEnabled.subpluginOption(multiplatformX.isViewModelEnabled),
                    MultiplatformxCommandLineProcessor.Options.viewModelAutoObserveEnabled.subpluginOption(multiplatformX.isComposableAutoObserveEnabled),
                )
            } else {
                listOf(
                    MultiplatformxCommandLineProcessor.Options.enabled.subpluginOption("false")
                )
            }
        }
    }

    override fun getCompilerPluginId() = MultiplatformxCommandLineProcessor.pluginId

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(groupId = BuildConfig.KOTLIN_PLUGIN_GROUP, artifactId = BuildConfig.KOTLIN_PLUGIN_NAME, version = BuildConfig.KOTLIN_PLUGIN_VERSION)

    override fun getPluginArtifactForNative(): SubpluginArtifact? =
        SubpluginArtifact(groupId = BuildConfig.KOTLIN_PLUGIN_GROUP, artifactId = BuildConfig.KOTLIN_NATIVE_PLUGIN_NAME, version = BuildConfig.KOTLIN_PLUGIN_VERSION)
}