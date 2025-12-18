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

package com.wire.kalium.logic.util

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.common.functional.Either
import okio.BufferedSource
import okio.Path
import okio.Sink
import okio.Source

internal expect fun createCompressedFile(files: List<Pair<Source, String>>, outputSink: Sink): Either<CoreFailure, Long>
internal expect fun extractCompressedFile(
    inputSource: Source,
    outputRootPath: Path,
    param: ExtractFilesParam,
    fileSystem: KaliumFileSystem
): Either<CoreFailure, Long>

internal expect fun checkIfCompressedFileContainsFileTypes(
    compressedFilePath: Path,
    fileSystem: KaliumFileSystem,
    expectedFileExtensions: List<String>
): Either<CoreFailure, Map<String, Boolean>>

internal sealed interface ExtractFilesParam {
    data object All : ExtractFilesParam
    data class Only(val files: Set<String>) : ExtractFilesParam {
        internal constructor(vararg files: String) : this(files.toSet())
    }
}

internal expect inline fun <reified T> decodeBufferSequence(bufferedSource: BufferedSource): Sequence<T>
