package com.wire.kalium.logic.data.asset

import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import okio.Path
import okio.Sink
import okio.Source

expect class KaliumFileSystemImpl constructor(dataStoragePaths: DataStoragePaths, dispatcher: KaliumDispatcher = KaliumDispatcherImpl) :
    KaliumFileSystem

interface KaliumFileSystem {
    /**
     * Provides the root of the cache path, used to store temporary files
     */
    val rootCachePath: Path

    /**
     * Opens an output stream that will be used to write the data on the given [outputPath]
     * @param outputPath the path where the data will be eventually written
     * @param mustCreate whether to force the creation of the outputPath if it doesn't exist on the current file system
     * @return the [Sink] stream of data to be written
     */
    fun sink(outputPath: Path, mustCreate: Boolean = false): Sink

    /**
     * Creates an input stream that will be used to read the data from the given [inputPath]
     * @param inputPath the path from where the data will be read
     * @return the [Source] stream of data to be read
     */
    fun source(inputPath: Path): Source

    /**
     * It will make sure the given [dir] gets created on the file system
     * @param mustCreate whether it is certain that [dir] doesn't exist and will need to be created
     */
    fun createDirectory(dir: Path, mustCreate: Boolean = true)

    /**
     * This will delete the content of the given [path]
     * @param path the path to be deleted
     * @param mustExist whether it is certain that [path] exists before the deletion
     */
    fun delete(path: Path, mustExist: Boolean = false)

    /**
     * Checks whether the given [path] is already created and exists on the current file system
     * @return whether the given [path] exists in the current file system
     */
    fun exists(path: Path): Boolean

    /**
     * Copies effectively the content of [sourcePath] into [targetPath]
     * @param sourcePath the path of the content to be copied
     * @param targetPath the destination path where the data will be copied into
     */
    fun copy(sourcePath: Path, targetPath: Path)

    /**
     * Creates a temporary path if it didn't exist before and returns it if successful
     * @param pathString a predefined temp path string. If not provided the temporary folder will be created with a default path
     */
    fun tempFilePath(pathString: String? = null): Path

    /**
     * Creates a persistent path on the internal storage folder of the file system if it didn't exist before and returns it if successful
     * @param assetName the asset path string
     */
    fun providePersistentAssetPath(assetName: String): Path

    /**
     * Fetches the persistent [Path] of the current user's avatar in the [KaliumFileSystem]
     */
    fun selfUserAvatarPath(): Path

    /**
     * Reads the data of the given path as a byte array
     * @param inputPath the path pointing to the stored data
     */
    suspend fun readByteArray(inputPath: Path): ByteArray

    /**
     * Writes the data contained on [dataSource] into the provided [outputSink]
     * @param outputSink the data sink used to write the data from [dataSource]
     * @param dataSource the data source that kaliumFileSystem will read from to write the data to the [outputSink]
     * @return the number of bytes written
     */
    suspend fun writeData(outputSink: Sink, dataSource: Source): Long
}
