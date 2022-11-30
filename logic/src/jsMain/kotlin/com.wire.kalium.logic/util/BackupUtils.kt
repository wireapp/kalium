package com.wire.kalium.logic.util

@Suppress("MayBeConst")
actual val clientPlatform: String = "jvm"

expect fun createCompressedFile(files: List<Pair<Source, String>>, outputSink: Sink): Either<CoreFailure, Long> =
    TODO("Implement own JS compression method")

actual fun extractCompressedFile(inputSource: Source, outputRootPath: Path, fileSystem: KaliumFileSystem): Either<CoreFailure, Long> =
    TODO("Implement own JS decompression method")
