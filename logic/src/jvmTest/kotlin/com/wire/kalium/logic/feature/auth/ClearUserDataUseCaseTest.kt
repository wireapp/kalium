/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.asset.AssetsStorageFolder
import com.wire.kalium.logic.data.asset.CacheFolder
import com.wire.kalium.logic.data.asset.DBFolder
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.userstorage.di.UserStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClearUserDataUseCaseTest {

    private val databaseFolder = Files.createTempDirectory("clear-user-data-").toFile()

    @AfterTest
    fun cleanUp() {
        databaseFolder.deleteRecursively()
    }

    @Test
    fun givenUserFilesOnDisk_whenClearingUserData_thenDatabaseAssetFolderAndCacheFolderAreDeleted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFileIn(ASSET_FOLDER, "attachment.pdf")
            .withFileIn(CACHE_FOLDER, "image_attachment.jpg")
            .arrange()

        useCase()

        assertNull(arrangement.userDatabase.dbFileLocation())
        assertFalse(arrangement.fileSystem.exists(ASSET_FOLDER.toPath()))
        assertFalse(arrangement.fileSystem.exists(CACHE_FOLDER.toPath()))
    }

    @Test
    fun givenNoUserFoldersOnDisk_whenClearingUserData_thenDoesNotThrow() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withoutFolders()
            .arrange()

        useCase()

        assertNull(arrangement.userDatabase.dbFileLocation())
    }

    @Test
    fun givenAssetFolderDeletionFails_whenClearingUserData_thenDoesNotThrowAndStillDeletesCacheFolder() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFileIn(ASSET_FOLDER, "attachment.pdf")
            .withFileIn(CACHE_FOLDER, "image_attachment.jpg")
            .withDeletionFailingFor(ASSET_FOLDER)
            .arrange()

        useCase()

        assertTrue(arrangement.fileSystem.exists(ASSET_FOLDER.toPath()))
        assertFalse(arrangement.fileSystem.exists(CACHE_FOLDER.toPath()))
    }

    private inner class Arrangement {
        val userDatabase = userDatabaseBuilder(
            PlatformDatabaseData(StorageData.FileBacked(databaseFolder)),
            UserIDEntity("user", "wire.test"), null, Dispatchers.IO
        )
        val fileSystem = FakeKaliumFileSystem()
        private var failingFolder: String? = null

        init {
            assertNotNull(userDatabase.dbFileLocation())
        }

        fun withFileIn(folder: String, name: String) = apply {
            fileSystem.createDirectories(folder.toPath())
            fileSystem.sink("$folder/$name".toPath(), mustCreate = false).buffer().use { it.writeUtf8("content") }
        }

        fun withoutFolders() = apply {
            fileSystem.deleteContents(ASSET_FOLDER.toPath(), mustExist = false)
            fileSystem.deleteContents(CACHE_FOLDER.toPath(), mustExist = false)
        }

        fun withDeletionFailingFor(folder: String) = apply { failingFolder = folder }

        fun arrange(): Pair<Arrangement, ClearUserDataUseCase> {
            val failing = failingFolder
            val kaliumFileSystem: KaliumFileSystem = object : KaliumFileSystem by fileSystem {
                override fun deleteContents(dir: Path, mustExist: Boolean) {
                    if (dir.toString() == failing) throw IllegalStateException("Cannot delete $dir")
                    fileSystem.deleteContents(dir, mustExist)
                }
            }
            val useCase = ClearUserDataUseCaseImpl(
                userStorage = UserStorage(userDatabase),
                dataStoragePaths = DataStoragePaths(AssetsStorageFolder(ASSET_FOLDER), CacheFolder(CACHE_FOLDER), DBFolder(DB_FOLDER)),
                kaliumFileSystem = kaliumFileSystem
            )
            return this to useCase
        }
    }

    private companion object {
        const val ASSET_FOLDER = "/Users/me/testApp/files"
        const val CACHE_FOLDER = "/Users/me/testApp/cache"
        const val DB_FOLDER = "/Users/me/testApp/database"
    }
}
