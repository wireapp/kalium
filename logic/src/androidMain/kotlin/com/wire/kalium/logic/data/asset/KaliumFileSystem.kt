package com.wire.kalium.logic.data.asset

import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import okio.use

actual class KaliumFileSystem actual constructor(
    private val dataStoragePaths: DataStoragePaths,
    private val dispatcher: KaliumDispatcher
) : FileSystem() {
    override fun appendingSink(file: Path, mustExist: Boolean): Sink = SYSTEM.appendingSink(file, mustExist)

    override fun atomicMove(source: Path, target: Path) = SYSTEM.atomicMove(source, target)

    override fun canonicalize(path: Path): Path = SYSTEM.canonicalize(path)

    override fun createDirectory(dir: Path, mustCreate: Boolean) = SYSTEM.createDirectory(dir, mustCreate)

    override fun createSymlink(source: Path, target: Path) = SYSTEM.createSymlink(source, target)

    override fun delete(path: Path, mustExist: Boolean) = SYSTEM.delete(path, mustExist)

    override fun list(dir: Path): List<Path> = SYSTEM.list(dir)

    override fun listOrNull(dir: Path): List<Path>? = SYSTEM.listOrNull(dir)

    override fun metadataOrNull(path: Path): FileMetadata? = SYSTEM.metadataOrNull(path)

    override fun sink(file: Path, mustCreate: Boolean): Sink = SYSTEM.sink(file, mustCreate)

    override fun source(file: Path): Source = SYSTEM.source(file)

    override fun openReadOnly(file: Path): FileHandle = SYSTEM.openReadOnly(file)

    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle =
        SYSTEM.openReadWrite(file, mustCreate, mustExist)

    /**
     * Creates a temporary path if it didn't exist before and returns it if successful
     * @param pathString a predefined temp path string. If not provided the temporary folder will be created with a default path
     */
    actual fun tempFilePath(pathString: String?): Path {
        val filePath = pathString ?: "temp_file_path"
        return "${dataStoragePaths.cachePath.value}/$filePath".toPath()
    }

    /**
     * Creates a persistent path on the internal storage folder of the file system if it didn't exist before and returns it if successful
     * @param assetName the asset path string
     */
    actual fun providePersistentAssetPath(assetName: String): Path = "${dataStoragePaths.assetStoragePath.value}/$assetName".toPath()

    /**
     * Reads the data of the given path as a byte array
     * @param inputPath the path pointing to the stored data
     */
    actual suspend fun readByteArray(inputPath: Path): ByteArray = source(inputPath).use {
        withContext(dispatcher.io) {
            it.buffer().use { bufferedFileSource ->
                bufferedFileSource.readByteArray()
            }
        }
    }

    /**
     * Writes the data contained on [dataSource] into the provided [outputPath]
     * @return the number of bytes written
     */
    actual suspend fun writeData(outputPath: Path, dataSource: Source): Long {
        var byteCount = 0L
        withContext(dispatcher.io) {
            sink(outputPath).use { sink ->
                sink.buffer().use { bufferedFileSink ->
                    byteCount = bufferedFileSink.writeAll(dataSource)
                }
            }
        }
        return byteCount
    }
}
