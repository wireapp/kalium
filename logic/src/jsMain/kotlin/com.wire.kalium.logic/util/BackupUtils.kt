package com.wire.kalium.logic.util

actual const val CLIENT_PLATFORM: String = "js"

expect fun createCompressedFile(files: List<Pair<Source, String>>, outputSink: Sink): Either<CoreFailure, Long> =
    TODO("Implement own JS compression method")

actual fun extractCompressedFile(inputSource: Source, outputRootPath: Path, fileSystem: KaliumFileSystem): Either<CoreFailure, Long> =
    TODO("Implement own JS decompression method")
