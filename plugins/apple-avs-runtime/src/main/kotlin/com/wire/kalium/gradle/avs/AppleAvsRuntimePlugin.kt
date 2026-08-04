/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.gradle.avs

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

public class AppleAvsRuntimePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        val runtimeTasks = registerAvsRuntimeTasks()
        configureKotlinMultiplatform(runtimeTasks)
    }

    private fun Project.registerAvsRuntimeTasks(): AvsRuntimeTasks {
        val runtimeDirectory = layout.buildDirectory.dir("kalium-apple-avs-runtime")
        val cache = avsCacheLocations()
        val prepareArchive = registerPrepareAvsRuntimeTask(runtimeDirectory, cache)
        val extractXcframework = registerExtractAvsRuntimeTask(
            runtimeDirectory,
            cache,
            prepareArchive,
        )
        registerAvsDebugSymbolsTask(runtimeDirectory, cache, prepareArchive)
        registerAvsXcodeTasks(runtimeDirectory, cache.extractedDirectory, extractXcframework)
        return AvsRuntimeTasks(extractXcframework, cache.extractedDirectory)
    }

    private fun Project.avsCacheLocations(): AvsCacheLocations {
        val cacheRoot = providers.gradleProperty("kalium.avs.cacheDirectory")
            .map(rootProject::file)
            .getOrElse(
                gradle.gradleUserHomeDir.resolve("caches/kalium-apple-avs-runtime")
            )
        val cacheKeyDirectory = cacheRoot.resolve(AvsRuntimeArtifact.archiveSha256)
        val archiveFile = layout.file(
            providers.provider {
                cacheKeyDirectory.resolve("avs.xcframework.zip")
            }
        )
        val extractedDirectory = layout.dir(
            providers.provider {
                cacheKeyDirectory.resolve(frameworkCacheDirectoryName())
            }
        )
        return AvsCacheLocations(archiveFile, extractedDirectory)
    }

    private fun Project.registerPrepareAvsRuntimeTask(
        runtimeDirectory: Provider<Directory>,
        cache: AvsCacheLocations,
    ): TaskProvider<PrepareAvsXcframeworkTask> =
        tasks.register(
            "prepareAvsXcframeworkArchive",
            PrepareAvsXcframeworkTask::class.java,
        ) {
            group = TASK_GROUP
            description = "Downloads or copies and verifies the Apple AVS XCFramework"
            archiveUrl.set(AvsRuntimeArtifact.archiveUrl)
            archiveUrl.disallowChanges()
            expectedSha256.set(AvsRuntimeArtifact.archiveSha256)
            expectedSha256.disallowChanges()
            localArchive.set(
                providers.gradleProperty("kalium.avs.archive")
                    .map { layout.projectDirectory.file(it) }
            )
            cachedArchive.set(cache.archiveFile)
            outputMarker.set(runtimeDirectory.map { it.file("state/archive-ready") })
            outputs.upToDateWhen {
                isArchiveCacheReady(
                    cache.archiveFile.get().asFile,
                    AvsRuntimeArtifact.archiveSha256,
                )
            }
        }

    private fun Project.registerExtractAvsRuntimeTask(
        runtimeDirectory: Provider<Directory>,
        cache: AvsCacheLocations,
        prepareArchive: TaskProvider<PrepareAvsXcframeworkTask>,
    ): TaskProvider<ExtractAvsXcframeworkTask> =
        tasks.register(
            "extractAvsXcframework",
            ExtractAvsXcframeworkTask::class.java,
        ) {
            group = TASK_GROUP
            description = "Caches the normalized Apple AVS framework slices"
            dependsOn(prepareArchive)
            expectedSha256.set(AvsRuntimeArtifact.archiveSha256)
            expectedSha256.disallowChanges()
            cachedArchive.set(cache.archiveFile)
            cachedXcframework.set(cache.extractedDirectory)
            outputMarker.set(runtimeDirectory.map { it.file("state/frameworks-ready") })
            outputs.upToDateWhen {
                isFrameworkCacheReady(
                    cache.extractedDirectory.get().asFile,
                    AvsRuntimeArtifact.archiveSha256,
                )
            }
        }

    private fun Project.registerAvsDebugSymbolsTask(
        runtimeDirectory: Provider<Directory>,
        cache: AvsCacheLocations,
        prepareArchive: TaskProvider<PrepareAvsXcframeworkTask>,
    ) {
        tasks.register("extractAvsDebugSymbols", ExtractAvsDebugSymbolsTask::class.java) {
            group = TASK_GROUP
            description = "Extracts AVS dSYMs for crash symbolication"
            dependsOn(prepareArchive)
            cachedArchive.set(cache.archiveFile)
            expectedSha256.set(AvsRuntimeArtifact.archiveSha256)
            outputDirectory.set(runtimeDirectory.map { it.dir("debug-symbols") })
        }
    }

    private fun Project.registerAvsXcodeTasks(
        runtimeDirectory: Provider<Directory>,
        extractedDirectory: Provider<Directory>,
        extractXcframework: TaskProvider<ExtractAvsXcframeworkTask>,
    ) {
        tasks.register("generateAvsXcodeConfig", GenerateAvsXcodeConfigTask::class.java) {
            group = TASK_GROUP
            description = "Generates an xcconfig that links the correct AVS XCFramework slice"
            dependsOn(extractXcframework)
            extractedXcframework.set(extractedDirectory)
            extractedXcframeworkMarker.set(extractXcframework.flatMap { it.outputMarker })
            macosMinimumVersion.set(AvsRuntimeArtifact.macosMinimumVersion)
            outputFile.set(runtimeDirectory.map { it.file("xcode/KaliumAvsRuntime.xcconfig") })
        }

        tasks.register("embedAvsForXcode", EmbedAvsForXcodeTask::class.java) {
            group = TASK_GROUP
            description = "Embeds and signs AVS in the current Xcode application build"
            dependsOn(extractXcframework)
            extractedXcframework.set(extractedDirectory)
            extractedXcframeworkMarker.set(extractXcframework.flatMap { it.outputMarker })
            platformName.set(providers.environmentVariable("PLATFORM_NAME"))
            targetBuildDirectory.set(providers.environmentVariable("TARGET_BUILD_DIR"))
            frameworksFolderPath.set(
                providers.environmentVariable("FRAMEWORKS_FOLDER_PATH").orElse("Frameworks")
            )
            codeSignIdentity.set(providers.environmentVariable("EXPANDED_CODE_SIGN_IDENTITY"))
            codeSignIdentityName.set(
                providers.environmentVariable("EXPANDED_CODE_SIGN_IDENTITY_NAME")
            )
            codeSigningAllowed.set(
                providers.environmentVariable("CODE_SIGNING_ALLOWED")
                    .map { it.equals("YES", ignoreCase = true) }
                    .orElse(false)
            )
        }
    }

    private fun Project.configureKotlinMultiplatform(runtimeTasks: AvsRuntimeTasks) {
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlin.targets.withType(KotlinNativeTarget::class.java).configureEach {
                val platform = AvsApplePlatform.forKotlinTarget(name) ?: return@configureEach
                val frameworkDirectory = runtimeTasks.extractedDirectory.get()
                    .dir("avs.xcframework/${platform.xcframeworkSlice}")
                    .asFile
                    .absolutePath

                binaries.configureEach {
                    if (platform == AvsApplePlatform.MACOS) {
                        freeCompilerArgs +=
                            "-Xoverride-konan-properties=" +
                            "osVersionMin.macos_arm64=${AvsRuntimeArtifact.macosMinimumVersion}"
                    }
                    linkerOpts("-F$frameworkDirectory", "-framework", "avs")
                    linkTaskProvider.configure {
                        dependsOn(runtimeTasks.extractXcframework)
                    }

                    val shouldStageRuntime =
                        platform in STANDALONE_EXECUTABLE_PLATFORMS &&
                            (this is Executable || this is TestExecutable)
                    if (shouldStageRuntime) {
                        linkerOpts("-Wl,-rpath,@executable_path/Frameworks")
                        val binary = this
                        val stageTaskName = "stageAvsFor${linkTaskName.removePrefix("link")}"
                        val stageTask = tasks.register(stageTaskName, Sync::class.java) {
                            group = TASK_GROUP
                            description = "Stages AVS beside ${binary.outputFile.name}"
                            dependsOn(runtimeTasks.extractXcframework, binary.linkTaskProvider)
                            from(
                                runtimeTasks.extractedDirectory.map {
                                    it.dir(
                                        "avs.xcframework/${platform.xcframeworkSlice}/avs.framework"
                                    )
                                }
                            )
                            into(binary.outputDirectory.resolve("Frameworks/avs.framework"))
                        }
                        linkTaskProvider.configure {
                            finalizedBy(stageTask)
                        }
                        if (binary is Executable) {
                            binary.runTaskProvider?.configure {
                                dependsOn(stageTask)
                            }
                        }
                    }
                }
            }
        }
    }

    private data class AvsRuntimeTasks(
        val extractXcframework: TaskProvider<ExtractAvsXcframeworkTask>,
        val extractedDirectory: Provider<Directory>,
    )

    private data class AvsCacheLocations(
        val archiveFile: Provider<RegularFile>,
        val extractedDirectory: Provider<Directory>,
    )

    private companion object {
        const val TASK_GROUP = "kalium apple avs"
        val STANDALONE_EXECUTABLE_PLATFORMS = setOf(
            AvsApplePlatform.IOS_SIMULATOR,
            AvsApplePlatform.MACOS,
        )
    }
}
