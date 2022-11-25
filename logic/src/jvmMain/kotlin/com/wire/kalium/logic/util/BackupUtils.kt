package com.wire.kalium.logic.util

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.functional.Either
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual val clientPlatform: String = "jvm"

actual fun createCompressedFile(files: List<Pair<Source, String>>, outputSink: Sink): Either<CoreFailure, Unit> = try {
    ZipOutputStream(outputSink.buffer().outputStream()).use { zipOutputStream ->
        files.forEach { (fileSource, fileName) ->
            val entry = ZipEntry(fileName)
            zipOutputStream.putNextEntry(entry)
            fileSource.buffer().use { input ->
                zipOutputStream.write(input.readByteArray())
            }
            zipOutputStream.closeEntry()
        }
    }
    Either.Right(Unit)
} catch (e: Exception) {
    Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to create and compress the backup file", e)))
}

actual fun extractCompressedFile(inputSource: Source, outputRootPath: Path, fileSystem: KaliumFileSystem): Either<CoreFailure, Unit> = try {
    ZipInputStream(inputSource.buffer().inputStream()).use { zipInputStream ->
        var entry: ZipEntry? = zipInputStream.nextEntry
        var entryPathName: Path
        while (entry != null) {
            entryPathName = "${outputRootPath}/${entry.name}".toPath()
            val outputSink = fileSystem.sink(entryPathName)
            outputSink.buffer().use { output ->
                output.write(zipInputStream.readBytes())
            }
            zipInputStream.closeEntry()
            entry = zipInputStream.nextEntry
        }
    }
    Either.Right(Unit)
} catch (e: Exception) {
    Either.Left(StorageFailure.Generic(RuntimeException("There was an error trying to extract the backup file", e)))
}
