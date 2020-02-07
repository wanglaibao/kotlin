/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scripting.gradle.importing

import com.intellij.build.FilePosition
import com.intellij.build.SyncViewManager
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemEventDispatcher
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.configuration.cache.CachedConfigurationInputs
import org.jetbrains.kotlin.idea.core.script.configuration.cache.ScriptConfigurationSnapshot
import org.jetbrains.kotlin.idea.framework.GRADLE_SYSTEM_ID
import org.jetbrains.kotlin.idea.scripting.gradle.GradleScriptInputsWatcher
import org.jetbrains.kotlin.idea.scripting.gradle.getGradleScriptInputsStamp
import org.jetbrains.kotlin.idea.scripting.gradle.saveGradleProjectRootsAfterImport
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.adjustByDefinition
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.GradleUtil
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

const val gradle_build_script_errors = "Gradle Kotlin DSL scripts contain errors"
private const val gradle_build_script_errors_group = "Gradle Kotlin DSL Scripts errors"

@Suppress("UnstableApiUsage")
class KotlinDslScriptModelsUpdater : ExternalSystemTaskNotificationListenerAdapter() {
    companion object {
        private var kotlinDslScriptsModels: List<KotlinDslScriptModel>? = null

        fun addModels(new: List<KotlinDslScriptModel>) {
            kotlinDslScriptsModels = new + (kotlinDslScriptsModels ?: emptyList())
        }
    }

    override fun onStart(id: ExternalSystemTaskId, workingDir: String) {
        if (id.type == ExternalSystemTaskType.RESOLVE_PROJECT && id.projectSystemId == GRADLE_SYSTEM_ID) {
            id.findProject()?.KOTLIN_DSL_SCRIPT_MODELS?.clear()
        }
    }

    override fun onEnd(id: ExternalSystemTaskId) {
        if (id.type == ExternalSystemTaskType.RESOLVE_PROJECT && id.projectSystemId == GRADLE_SYSTEM_ID) {
            val project: Project = id.findProject() ?: return

            val gradleSettings = ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID)
            val projectSettings = gradleSettings.getLinkedProjectsSettings()
                .filterIsInstance<GradleProjectSettings>()
                .firstOrNull() ?: return

            saveGradleProjectRootsAfterImport(
                projectSettings.modules.takeIf { it.isNotEmpty() } ?: setOf(projectSettings.externalProjectPath)
            )

            val gradleExeSettings = ExternalSystemApiUtil.getExecutionSettings<GradleExecutionSettings>(
                project,
                projectSettings.externalProjectPath,
                GradleConstants.SYSTEM_ID,
            )
            val javaHome = File(gradleExeSettings.javaHome ?: return)

            val scriptConfigurations = mutableListOf<Pair<VirtualFile, ScriptConfigurationSnapshot>>()

            val gradleKotlinBuildScripts = project.KOTLIN_DSL_SCRIPT_MODELS
            val buildScripts = gradleKotlinBuildScripts.toList()

            val syncViewManager = ServiceManager.getService(project, SyncViewManager::class.java)
            val buildEventDispatcher = ExternalSystemEventDispatcher(id, syncViewManager)

            buildScripts.forEach { buildScript ->
                val scriptFile = File(buildScript.file)
                val virtualFile = VfsUtil.findFile(scriptFile.toPath(), true)!!

                val inputs = getGradleScriptInputsStamp(project, virtualFile, givenTimeStamp = buildScript.inputsTimeStamp)

                val definition = virtualFile.findScriptDefinition(project) ?: return@forEach

                val configuration =
                    definition.compilationConfiguration.with {
                        jvm.jdkHome(javaHome)
                        defaultImports(buildScript.imports)
                        dependencies(JvmDependency(buildScript.classPath.map { File(it) }))
                        ide.dependenciesSources(JvmDependency(buildScript.sourcePath.map { File(it) }))
                    }.adjustByDefinition(definition)

                scriptConfigurations.add(
                    Pair(
                        virtualFile,
                        ScriptConfigurationSnapshot(
                            inputs ?: CachedConfigurationInputs.OutOfDate,
                            listOf(),
                            ScriptCompilationConfigurationWrapper.FromCompilationConfiguration(
                                VirtualFileScriptSource(virtualFile),
                                configuration,
                            ),
                        ),
                    ),
                )

                buildScript.messages.forEach {
                    val severity = when (it.severity) {
                        KotlinDslScriptModel.Severity.WARNING -> MessageEvent.Kind.WARNING
                        KotlinDslScriptModel.Severity.ERROR -> MessageEvent.Kind.ERROR
                    }
                    val position = it.position
                    if (position == null) {
                        buildEventDispatcher.onEvent(
                            id,
                            MessageEventImpl(id, severity, gradle_build_script_errors_group, it.text, it.details)
                        )
                    } else {
                        buildEventDispatcher.onEvent(
                            id,
                            FileMessageEventImpl(
                                id,
                                severity,
                                gradle_build_script_errors_group,
                                it.text, it.details,
                                FilePosition(scriptFile, position.line, position.column)
                            ),
                        )
                    }
                }
            }

            project.service<ScriptConfigurationManager>().saveCompilationConfigurationAfterImport(scriptConfigurations)
            project.service<GradleScriptInputsWatcher>().clearState()
        }
    }
}