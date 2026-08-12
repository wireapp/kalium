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
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "The content-addressed AVS cache is shared outside task outputs")
public abstract class ExtractAvsXcframeworkTask : DefaultTask() {
    @get:Input
    public abstract val expectedSha256: Property<String>

    @get:Internal
    public abstract val cachedArchive: RegularFileProperty

    @get:Internal
    public abstract val cachedXcframework: DirectoryProperty

    @get:OutputFile
    public abstract val outputMarker: RegularFileProperty

    @get:Inject
    protected abstract val archiveOperations: ArchiveOperations

    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @TaskAction
    public fun extractAndNormalize() {
        val checksum = expectedSha256.get()
        AppleAvsRuntimeCacheService.extractFrameworks(
            archive = cachedArchive.get().asFile,
            destination = cachedXcframework.get().asFile,
            expectedSha256 = checksum,
            archiveOperations = archiveOperations,
            fileSystemOperations = fileSystemOperations,
            execOperations = execOperations,
        )

        outputMarker.get().asFile.apply {
            parentFile.mkdirs()
            writeText(cacheMarker(checksum))
        }
    }
}
