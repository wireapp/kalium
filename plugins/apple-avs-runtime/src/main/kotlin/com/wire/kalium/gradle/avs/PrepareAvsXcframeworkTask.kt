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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "The content-addressed AVS cache is shared outside task outputs")
public abstract class PrepareAvsXcframeworkTask : DefaultTask() {
    @get:Input
    public abstract val archiveUrl: Property<String>

    @get:Input
    public abstract val expectedSha256: Property<String>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val localArchive: RegularFileProperty

    @get:Internal
    public abstract val cachedArchive: RegularFileProperty

    @get:OutputFile
    public abstract val outputMarker: RegularFileProperty

    @TaskAction
    public fun prepare() {
        val checksum = expectedSha256.get()
        AppleAvsRuntimeCacheService.prepareArchive(
            archiveUrl = archiveUrl.get(),
            expectedSha256 = checksum,
            localArchive = localArchive.orNull?.asFile,
            destination = cachedArchive.get().asFile,
        )

        outputMarker.get().asFile.apply {
            parentFile.mkdirs()
            writeText(cacheMarker(checksum))
        }
    }
}
