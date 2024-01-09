/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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

@Suppress("TooManyFunctions")
actual class KaliumFileSystemImpl actual constructor(
    private val dataStoragePaths: DataStoragePaths,
    private val dispatcher: KaliumDispatcher
) : KaliumFileSystem {

    /**
     * Provides the root of the cache path, used to store temporary files
     */
    override val rootCachePath: Path = dataStoragePaths.cachePath.value.toPath()

    /**
     * Provides the root of the current user database path, used to store all the Database information.
     */
    override val rootDBPath: Path = dataStoragePaths.dbPath.value.toPath()

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
     * It will create the provided [dir] in the current file system. It will fail if the parent directory doesn't exist
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
     * This will delete recursively the given [dir] and all its content
     * @param dir the directory to be deleted
     * @param mustExist whether it is certain that [dir] exists before the deletion
     */
    override fun deleteContents(dir: Path, mustExist: Boolean) = SYSTEM.deleteRecursively(dir, mustExist)

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

    /**
     * Provides a list of paths found in the given [dir] path from where the call is being invoked.
     * @param dir the path from where the list of paths will be fetched
     * @return the list of paths found.
     */
    override suspend fun listDirectories(dir: Path): List<Path> = SYSTEM.list(dir)
}
