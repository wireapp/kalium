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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "The destination is an Xcode-owned build directory")
public abstract class EmbedAvsForXcodeTask : DefaultTask() {
    @get:Internal
    public abstract val extractedXcframework: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val extractedXcframeworkMarker: RegularFileProperty

    @get:Input
    public abstract val platformName: Property<String>

    @get:Input
    public abstract val targetBuildDirectory: Property<String>

    @get:Input
    public abstract val frameworksFolderPath: Property<String>

    @get:Input
    @get:Optional
    public abstract val codeSignIdentity: Property<String>

    /** Human-readable identity, used only to detect Developer ID distribution signing. */
    @get:Input
    @get:Optional
    public abstract val codeSignIdentityName: Property<String>

    @get:Input
    public abstract val codeSigningAllowed: Property<Boolean>

    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @TaskAction
    public fun embedAndSign() {
        val platform = AvsApplePlatform.forXcodePlatform(platformName.get())
            ?: throw GradleException("Unsupported Xcode PLATFORM_NAME '${platformName.get()}'")
        val source = extractedXcframework.get().asFile
            .resolve("avs.xcframework/${platform.xcframeworkSlice}/avs.framework")
        if (!source.isDirectory) {
            throw GradleException("AVS framework slice not found: ${source.absolutePath}")
        }

        val destinationParent = File(targetBuildDirectory.get(), frameworksFolderPath.get())
        val destination = destinationParent.resolve("avs.framework")
        fileSystemOperations.sync {
            from(source)
            into(destination)
        }

        val identity = codeSignIdentity.orNull.orEmpty()
        if (codeSigningAllowed.get() && identity.isNotBlank()) {
            // A secure timestamp is required for Developer ID distribution and notarization, and is
            // pointless for iOS and local development signing. Hardened runtime is deliberately not
            // applied here: it belongs on the app and its executables, not on a nested framework.
            val requiresSecureTimestamp = platform == AvsApplePlatform.MACOS &&
                codeSignIdentityName.orNull.orEmpty().startsWith(DEVELOPER_ID_IDENTITY_PREFIX)
            execOperations.exec {
                commandLine(
                    buildList {
                        add("/usr/bin/codesign")
                        add("--force")
                        add("--sign")
                        add(identity)
                        // Frameworks must not carry entitlements, so only the identifier is kept.
                        add("--preserve-metadata=identifier")
                        if (requiresSecureTimestamp) add("--timestamp")
                        add(destination.absolutePath)
                    }
                )
            }
        }

        logger.lifecycle("Embedded AVS framework at ${destination.absolutePath}")
    }

    private companion object {
        const val DEVELOPER_ID_IDENTITY_PREFIX = "Developer ID"
    }
}
