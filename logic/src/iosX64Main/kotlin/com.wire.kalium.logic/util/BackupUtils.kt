package com.wire.kalium.logic.util

expect fun createCompressedFile(files: List<Pair<Source, String>>, outputSink: Sink): Either<CoreFailure, Long> =
    TODO("Implement own iOS compression method")

actual fun extractCompressedFile(inputSource: Source, outputRootPath: Path, fileSystem: KaliumFileSystem): Either<CoreFailure, Long> =
    TODO("Implement own iOS decompression method")

actual  fun checkIfCompressedFileContainsFileType(compressedFilePath: Path, expectedFileExtension: String): Either<CoreFailure, Boolean> =
    TODO("Implement own iOS decompression method")
