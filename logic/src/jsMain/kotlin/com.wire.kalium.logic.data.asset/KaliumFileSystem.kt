package com.wire.kalium.logic.data.asset

import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Sink
import okio.Source

actual class KaliumFileSystem {

    actual fun sink(file: Path, mustCreate: Boolean): Sink {
        TODO("Not yet implemented")
    }

    actual fun source(file: Path): Source {
        TODO("Not yet implemented")
    }

    actual fun createDirectory(dir: Path, mustCreate: Boolean): Source {
        TODO("Not yet implemented")
    }

    actual fun delete(path: Path, mustExist: Boolean) = TODO("Not yet implemented")

    actual fun exists(path: Path): Boolean = TODO("Not yet implemented")

    actual fun copy(sourcePath: Path, targetPath: Path) = TODO("Not yet implemented")

    actual fun tempFilePath(pathString: String?): Path = TODO("Not yet implemented")

    actual fun providePersistentAssetPath(assetName: String): Path = TODO("Not yet implemented")

    actual suspend fun readByteArray(inputPath: Path): ByteArray = TODO("Not yet implemented")

    actual suspend fun writeData(outputPath: Path, dataSource: Source): Long = TODO("Not yet implemented")

    actual suspend fun writeData(outputPath: Path, dataBlob: ByteArray) = TODO("Not yet implemented")

    actual fun selfUserAvatarPath(): Path = TODO("Not yet implemented")
}
