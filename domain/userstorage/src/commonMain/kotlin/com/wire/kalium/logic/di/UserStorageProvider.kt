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

package com.wire.kalium.userstorage.di

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.db.UserDatabaseBuilder

public data class UserStorage(public val database: UserDatabaseBuilder)
public abstract class UserStorageProvider {
    private val inMemoryUserStorage: ConcurrentMutableMap<UserId, UserStorage> = ConcurrentMutableMap()

    public fun get(userId: UserId): UserStorage? = inMemoryUserStorage[userId]

    public fun getOrCreate(
        userId: UserId,
        platformUserStorageProperties: PlatformUserStorageProperties,
        shouldEncryptData: Boolean = true,
        dbInvalidationControlEnabled: Boolean,
    ): UserStorage = inMemoryUserStorage.computeIfAbsent(userId) {
        create(userId, shouldEncryptData, platformUserStorageProperties, dbInvalidationControlEnabled)
    }

    protected abstract fun create(
        userId: UserId,
        shouldEncryptData: Boolean,
        platformProperties: PlatformUserStorageProperties,
        dbInvalidationControlEnabled: Boolean
    ): UserStorage

    public fun remove(userId: UserId): UserStorage? = inMemoryUserStorage.remove(userId)
}

public expect class PlatformUserStorageProperties
