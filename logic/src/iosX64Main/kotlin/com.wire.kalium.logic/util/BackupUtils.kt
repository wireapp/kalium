package com.wire.kalium.logic.util

expect fun createCompressedFile(files: List<Pair<Source, String>>, outputSink: Sink): Either<CoreFailure, Long> =
    TODO("Implement own iOS compression method")

actual fun extractCompressedFile(inputSource: Source, outputRootPath: Path, fileSystem: KaliumFileSystem): Either<CoreFailure, Long> =
    TODO("Implement own iOS decompression method")

actual fun checkIfCompressedFileContainsFileTypes(
    compressedFilePath: Path,
    fileSystem: KaliumFileSystem,
    expectedFileExtensions: List<String>
): Either<CoreFailure, Map<String, Boolean>> =
    TODO("Implement own iOS decompression method")
