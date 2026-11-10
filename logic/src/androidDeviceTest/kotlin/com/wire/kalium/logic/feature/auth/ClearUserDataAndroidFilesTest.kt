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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wire.kalium.logic.data.asset.AssetsStorageFolder
import com.wire.kalium.logic.data.asset.CacheFolder
import com.wire.kalium.logic.data.asset.DBFolder
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.asset.KaliumFileSystemImpl
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.inMemoryDatabase
import com.wire.kalium.userstorage.di.UserStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Real Android `filesDir` and `cacheDir`, laid out as in `UserSessionScopeProviderImpl.create`:
 * `<dir>/<domain>/<userId>`. The asset folder holds the `proteus/` and `mls/` keystores.
 */
class ClearUserDataAndroidFilesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val domain = "wire.test"
    private val wipedUser = "wiped-user-${System.nanoTime()}"
    private val otherUser = "other-user-${System.nanoTime()}"

    private fun assetDir(user: String) = File(context.filesDir, "$domain/$user")
    private fun cacheDir(user: String) = File(context.cacheDir, "$domain/$user")

    private fun fill(user: String): List<File> = listOf(
        File(assetDir(user), "attachment.pdf"),
        File(assetDir(user), "proteus/keystore"),
        File(assetDir(user), "mls/keystore"),
        File(cacheDir(user), "image_attachment.jpg"),
        File(cacheDir(user), "backup-create-workdir/backup.zip"),
    ).onEach {
        it.parentFile!!.mkdirs()
        it.writeText("content")
    }

    @After
    fun cleanUp() {
        listOf(wipedUser, otherUser).forEach {
            assetDir(it).deleteRecursively()
            cacheDir(it).deleteRecursively()
        }
    }

    @Test
    fun givenFilesInPerUserFolders_whenClearingUserData_thenOnlyThatUsersAssetAndCacheFoldersAreDeleted() = runBlocking {
        val wipedFiles = fill(wipedUser)
        val otherFiles = fill(otherUser)
        assertTrue(wipedFiles.all { it.exists() })
        val paths = DataStoragePaths(
            AssetsStorageFolder(assetDir(wipedUser).path),
            CacheFolder(cacheDir(wipedUser).path),
            DBFolder(context.getDatabasePath("unused").path)
        )
        val useCase = ClearUserDataUseCaseImpl(
            userStorage = UserStorage(inMemoryDatabase(context, UserIDEntity(wipedUser, domain), Dispatchers.IO)),
            dataStoragePaths = paths,
            kaliumFileSystem = KaliumFileSystemImpl(paths)
        )

        useCase()

        assertFalse(assetDir(wipedUser).exists(), "asset folder still exists")
        assertFalse(cacheDir(wipedUser).exists(), "cache folder still exists")
        wipedFiles.forEach { assertFalse(it.exists(), "$it still exists") }
        assertTrue(otherFiles.all { it.exists() }, "another user's files were deleted")
    }
}
