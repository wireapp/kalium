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
import com.wire.kalium.util.KaliumDispatcherImpl
import okio.Path
import okio.Sink
import okio.Source

@Suppress("TooManyFunctions")
internal expect class KaliumFileSystemImpl constructor(
    dataStoragePaths: DataStoragePaths,
    dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : KaliumFileSystem {
    override val rootCachePath: Path
    override val rootDBPath: Path
    override fun sink(outputPath: Path, mustCreate: Boolean): Sink
    override fun source(inputPath: Path): Source
    override fun createDirectories(dir: Path)
    override fun createDirectory(dir: Path, mustCreate: Boolean)
    override fun delete(path: Path, mustExist: Boolean)
    override fun deleteContents(dir: Path, mustExist: Boolean)
    override fun exists(path: Path): Boolean
    override fun copy(sourcePath: Path, targetPath: Path)
    override fun tempFilePath(pathString: String?): Path
    override fun providePersistentAssetPath(assetName: String): Path
    override fun selfUserAvatarPath(): Path
    override suspend fun readByteArray(inputPath: Path): ByteArray
    override suspend fun writeData(outputSink: Sink, dataSource: Source): Long
    override suspend fun listDirectories(dir: Path): List<Path>
}
