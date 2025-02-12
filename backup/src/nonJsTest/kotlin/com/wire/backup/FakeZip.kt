/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.backup

import com.wire.backup.dump.FileZipper
import com.wire.backup.ingest.BackupFileUnzipper
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import kotlin.random.Random
import kotlin.random.nextUInt

/**
 * A fake Zip/Unzip that will just copy and paste files.
 *
 * Within the provided work directory, this will use directories as if they were zip archives.
 *
 * ### Zipping
 * When asked to zip files A, B and C. This will:
 * 1. create a random ID
 * 2. create a directory with that ID as a name
 * 3. copy A, B and C to the created directory
 * 4. return the random ID as the "zipped" content
 *
 * ### Unzipping
 *
 * **Requires zipping first**.
 * When asked to unzip a file, it will:
 * 1. read the file as a String
 * 2. assume this String is a random ID created during zipping
 * 3. return the path to the directory that corresponds to the random ID
 */
class FakeZip(fakeZipWorkRootPath: Path) : BackupFileUnzipper, FileZipper {

    private val workDirPath = fakeZipWorkRootPath / "fakeZip"
    private val fs = FileSystem.SYSTEM

    init {
        clear()
        if (!fs.exists(workDirPath)) {
            fs.createDirectories(workDirPath)
        }
    }

    override fun unzipBackup(zipPath: String): String {
        val entryName = fs.source(zipPath.toPath()).buffer().readByteString().utf8()
        val target = workDirPath / entryName
        return fs.canonicalize(target).toString()
    }

    override fun zip(entries: List<String>): String {
        val fakeZipDirectoryName = "zipFile_" + Random.nextUInt()
        val fakeZipDirectoryPath = workDirPath / fakeZipDirectoryName
        fs.createDirectories(fakeZipDirectoryPath)
        entries.forEach { entry ->
            val target = fakeZipDirectoryPath / entry.toPath().name
            fs.copy(entry.toPath(), target)
        }
        val fakeZipFile = workDirPath / ("$fakeZipDirectoryName.zip")
        val outputSink = fs.openReadWrite(fakeZipFile).sink().buffer()
        outputSink.writeUtf8(fakeZipDirectoryName)
        outputSink.flush()
        return fs.canonicalize(fakeZipFile).toString()
    }

    fun clear() {
        FileSystem.SYSTEM.deleteRecursively(workDirPath)
    }
}
