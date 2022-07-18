package com.wire.kalium.logic.data.asset

import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Sink
import okio.Source

actual class KaliumFileSystemImpl: KaliumFileSystem {

    override fun sink(file: Path, mustCreate: Boolean): Sink = TODO("Not yet implemented")

    override actual fun source(file: Path): Source = TODO("Not yet implemented")

    override actual fun createDirectory(dir: Path, mustCreate: Boolean): Source = TODO("Not yet implemented")

    override actual fun delete(path: Path, mustExist: Boolean) = TODO("Not yet implemented")

    override actual fun exists(path: Path): Boolean = TODO("Not yet implemented")

    override actual fun copy(sourcePath: Path, targetPath: Path) = TODO("Not yet implemented")

    override actual fun tempFilePath(pathString: String?): Path = TODO("Not yet implemented")

    override actual fun providePersistentAssetPath(assetName: String): Path = TODO("Not yet implemented")

    override actual suspend fun readByteArray(inputPath: Path): ByteArray = TODO("Not yet implemented")

    override actual suspend fun writeData(outputSink: Sink, dataSource: Source): Long = TODO("Not yet implemented")

    override actual fun selfUserAvatarPath(): Path = TODO("Not yet implemented")
}
