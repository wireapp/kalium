package com.wire.kalium.logic.util

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.functional.Either
import okio.Source
import okio.Sink
import okio.Path

actual fun createCompressedFile(files: List<Pair<Source, String>>, outputSink: Sink): Either<CoreFailure, Long> =
    TODO("Implement own iOS compression method")
actual fun extractCompressedFile(inputSource: Source, outputRootPath: Path, fileSystem: KaliumFileSystem): Either<CoreFailure, Long> =
    TODO("Implement own iOS compression method")
actual fun checkIfCompressedFileContainsFileTypes(
    compressedFilePath: Path,
    fileSystem: KaliumFileSystem,
    expectedFileExtensions: List<String>
): Either<CoreFailure, Map<String, Boolean>> =
    TODO("Implement own iOS decompression method")
