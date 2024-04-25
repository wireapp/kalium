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

package com.wire.kalium.logic.di

import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.inMemoryDatabase
import com.wire.kalium.persistence.db.userDatabaseBuilder
import com.wire.kalium.persistence.kmmSettings.UserPrefBuilder
import com.wire.kalium.util.KaliumDispatcherImpl

internal actual class PlatformUserStorageProvider : UserStorageProvider() {
    override fun create(
        userId: UserId,
        shouldEncryptData: Boolean,
        platformProperties: PlatformUserStorageProperties
    ): UserStorage {
        val userIdEntity = userId.toDao()
        val pref = UserPrefBuilder(userIdEntity, platformProperties.rootPath, shouldEncryptData)

        val databaseInfo = platformProperties.databaseInfo
        val database = when (databaseInfo) {
            is DatabaseStorageType.FiledBacked -> {
                val storageData = StorageData.FileBacked(databaseInfo.filePath)
                userDatabaseBuilder(
                    platformDatabaseData = PlatformDatabaseData(storageData),
                    userId = userIdEntity,
                    passphrase = null,
                    dispatcher = KaliumDispatcherImpl.io,
                    enableWAL = false,
                    shouldTune = false,
                )
            }

            is DatabaseStorageType.InMemory -> {
                inMemoryDatabase(userIdEntity, KaliumDispatcherImpl.io)
            }
        }
        return UserStorage(database, pref)
    }
}
