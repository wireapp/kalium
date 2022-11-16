package com.wire.kalium.logic.util

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import okio.Sink
import okio.Source
import okio.buffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

actual val clientPlatform: String = "jvm"

actual fun createCompressedFile(files: List<Pair<Source, String>>, outputSink: Sink): Either<CoreFailure, Unit> = try {
    ZipOutputStream(outputSink.buffer().outputStream()).use { zipOutputStream ->
        files.forEach { file ->
            val entry = ZipEntry(file.second)
            zipOutputStream.putNextEntry(entry)
            file.first.buffer().use { input ->
                zipOutputStream.write(input.readByteArray())
            }
            zipOutputStream.closeEntry()
        }
    }
    Either.Right(Unit)
} catch (e: Exception) {
    Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to create and compress the backup file", e)))
}
