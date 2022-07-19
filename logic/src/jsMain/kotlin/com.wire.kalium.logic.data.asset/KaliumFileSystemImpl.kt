package com.wire.kalium.logic.data.asset

import okio.Path
import okio.Sink
import okio.Source

actual class KaliumFileSystemImpl : KaliumFileSystem {

    override fun sink(outputPath: Path, mustCreate: Boolean): Sink = TODO("Not yet implemented")

    override fun source(inputPath: Path): Source = TODO("Not yet implemented")

    override fun createDirectory(dir: Path, mustCreate: Boolean) = TODO("Not yet implemented")

    override fun delete(path: Path, mustExist: Boolean) = TODO("Not yet implemented")

    override fun exists(path: Path): Boolean = TODO("Not yet implemented")

    override fun copy(sourcePath: Path, targetPath: Path) = TODO("Not yet implemented")

    override fun tempFilePath(pathString: String?): Path = TODO("Not yet implemented")

    override fun providePersistentAssetPath(assetName: String): Path = TODO("Not yet implemented")

    override suspend fun readByteArray(inputPath: Path): ByteArray = TODO("Not yet implemented")

    override suspend fun writeData(outputSink: Sink, dataSource: Source): Long = TODO("Not yet implemented")

    override fun selfUserAvatarPath(): Path = TODO("Not yet implemented")
}
