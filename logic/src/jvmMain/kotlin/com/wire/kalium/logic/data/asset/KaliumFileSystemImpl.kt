package com.wire.kalium.logic.data.asset

import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.withContext
import okio.FileSystem.Companion.SYSTEM
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import okio.use

actual class KaliumFileSystemImpl actual constructor(
    private val dataStoragePaths: DataStoragePaths,
    private val dispatcher: KaliumDispatcher
) : KaliumFileSystem {

    /**
     * Provides the root of the cache path, used to store temporary files
     */
    override val rootCachePath: Path = dataStoragePaths.cachePath.value.toPath()

    /**
     * Opens an output stream that will be used to write the data on the given [outputPath]
     * @param outputPath the path where the data will be eventually written
     * @param mustCreate whether to force the creation of the outputPath if it doesn't exist on the current file system
     * @return the [Sink] stream of data to be written
     */
    override fun sink(outputPath: Path, mustCreate: Boolean): Sink = SYSTEM.sink(outputPath, mustCreate)

    /**
     * Creates an input stream that will be used to read the data from the given [inputPath]
     * @param inputPath the path from where the data will be read
     * @return the [Source] stream of data to be read
     */
    override fun source(inputPath: Path): Source = SYSTEM.source(inputPath)

    /**
     * It will create the provided [dir] in the current file system along with the needed subdirectories if they were not created previously
     */
    override fun createDirectories(dir: Path) = SYSTEM.createDirectories(dir)

    /**
     * It will make sure the given [dir] gets created on the file system
     * @param mustCreate whether it is certain that [dir] doesn't exist and will need to be created
     */
    override fun createDirectory(dir: Path, mustCreate: Boolean) = SYSTEM.createDirectory(dir, mustCreate)

    /**
     * This will delete the content of the given [path]
     * @param path the path to be deleted
     * @param mustExist whether it is certain that [path] exists before the deletion
     */
    override fun delete(path: Path, mustExist: Boolean) = SYSTEM.delete(path, mustExist)

    /**
     * Checks whether the given [path] is already created and exists on the current file system
     * @return whether the given [path] exists in the current file system
     */
    override fun exists(path: Path): Boolean = SYSTEM.exists(path)

    /**
     * Copies effectively the content of [sourcePath] into [targetPath]
     * @param sourcePath the path of the content to be copied
     * @param targetPath the destination path where the data will be copied into
     */
    override fun copy(sourcePath: Path, targetPath: Path) = SYSTEM.copy(sourcePath, targetPath)

    /**
     * Creates a temporary path if it didn't exist before and returns it if successful
     * @param pathString a predefined temp path string. If not provided the temporary folder will be created with a default path
     */
    override fun tempFilePath(pathString: String?): Path {
        val filePath = pathString ?: "temp_file_path"
        return "$rootCachePath/$filePath".toPath()
    }

    /**
     * Creates a persistent path on the internal storage folder of the file system if it didn't exist before and returns it if successful
     * @param assetName the asset path string
     */
    override fun providePersistentAssetPath(assetName: String): Path = "${dataStoragePaths.assetStoragePath.value}/$assetName".toPath()

    /**
     * Reads the data of the given path as a byte array
     * @param inputPath the path pointing to the stored data
     */
    override suspend fun readByteArray(inputPath: Path): ByteArray = source(inputPath).use {
        withContext(dispatcher.io) {
            it.buffer().use { bufferedFileSource ->
                bufferedFileSource.readByteArray()
            }
        }
    }

    /**
     * Writes the data contained on [dataSource] into the provided [outputSink]
     * @param outputSink the data sink used to write the data from [dataSource]
     * @param dataSource the data source that kaliumFileSystem will read from to write the data to the [outputSink]
     * @return the number of bytes written
     */
    override suspend fun writeData(outputSink: Sink, dataSource: Source): Long {
        var byteCount = 0L
        withContext(dispatcher.io) {
            outputSink.use { sink ->
                sink.buffer().use { bufferedFileSink ->
                    byteCount = bufferedFileSink.writeAll(dataSource)
                }
            }
        }
        return byteCount
    }

    /**
     * Fetches the persistent [Path] of the current user's avatar in the [KaliumFileSystem]
     */
    override fun selfUserAvatarPath(): Path = providePersistentAssetPath("self_user_avatar.jpg")
}
