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

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.kmmSettings.UserPrefBuilder

data class UserStorage(val database: UserDatabaseBuilder, val preferences: UserPrefBuilder)
abstract class UserStorageProvider {
    private val inMemoryUserStorage: ConcurrentMutableMap<UserId, UserStorage> = ConcurrentMutableMap()
    fun getOrCreate(
        userId: UserId,
        platformUserStorageProperties: PlatformUserStorageProperties,
        shouldEncryptData: Boolean = true
    ): UserStorage = inMemoryUserStorage.computeIfAbsent(userId) {
        create(userId, shouldEncryptData, platformUserStorageProperties)
    }

    protected abstract fun create(
        userId: UserId,
        shouldEncryptData: Boolean,
        platformProperties: PlatformUserStorageProperties
    ): UserStorage

    fun clearInMemoryUserStorage(userId: UserId) = inMemoryUserStorage.remove(userId)
}

internal expect class PlatformUserStorageProvider constructor() : UserStorageProvider
expect class PlatformUserStorageProperties
