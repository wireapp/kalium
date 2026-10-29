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

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.userstorage.di.UserStorage
import okio.Path.Companion.toPath

/**
 * Clears the user data from the local storage, except for the client id:
 * the database, the asset folder and the cache folder.
 * A failure to delete a folder is logged and does not stop the logout.
 */
internal interface ClearUserDataUseCase {
    suspend operator fun invoke()
}

internal class ClearUserDataUseCaseImpl internal constructor(
    private val userStorage: UserStorage,
    private val dataStoragePaths: DataStoragePaths,
    private val kaliumFileSystem: KaliumFileSystem
) : ClearUserDataUseCase {

    override suspend operator fun invoke() {
        clearUserStorage()
        deleteFolder(dataStoragePaths.assetStoragePath.value)
        deleteFolder(dataStoragePaths.cachePath.value)
    }

    private fun clearUserStorage() {
        userStorage.database.nuke()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun deleteFolder(path: String) {
        try {
            kaliumFileSystem.deleteContents(path.toPath(), mustExist = false)
        } catch (e: Exception) {
            kaliumLogger.withTextTag(TAG).e("Failed to delete user folder during data wipe", e)
        }
    }

    private companion object {
        const val TAG = "ClearUserDataUseCase"
    }
}
