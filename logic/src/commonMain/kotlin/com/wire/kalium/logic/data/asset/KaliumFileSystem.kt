package com.wire.kalium.logic.data.asset

import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import okio.FileSystem
import okio.Path
import okio.Source

expect class KaliumFileSystem constructor(dataStoragePaths: DataStoragePaths, dispatcher: KaliumDispatcher = KaliumDispatcherImpl) :
    FileSystem {

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
     * Reads the data of the given path as a byte array
     * @param inputPath the path pointing to the stored data
     */
    suspend fun readByteArray(inputPath: Path): ByteArray

    /**
     * Writes the data contained on [dataSource] into the provided [outputPath]
     * @param outputPath the path where the data will be written
     * @param dataSource the data source that kaliumFileSystem will read from to write the data to the [outputPath]
     * @return the number of bytes written
     */
    suspend fun writeData(outputPath: Path, dataSource: Source): Long
}
