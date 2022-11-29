package com.wire.kalium.logic.util

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.functional.Either
import okio.Path
import okio.Sink
import okio.Source

expect val CLIENT_PLATFORM: String

expect fun createCompressedFile(files: List<Pair<Source, String>>, outputSink: Sink): Either<CoreFailure, Long>
expect fun extractCompressedFile(inputSource: Source, outputRootPath: Path, fileSystem: KaliumFileSystem): Either<CoreFailure, Long>
