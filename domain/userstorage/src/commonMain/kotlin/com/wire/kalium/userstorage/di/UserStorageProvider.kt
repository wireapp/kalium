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

package com.wire.kalium.userstorage.di

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.db.UserDatabaseBuilder

public data class UserStorage(public val database: UserDatabaseBuilder)

/**
 * Provides in-memory caching for [UserStorage] instances keyed by [UserId].
 *
 * Cache scope is controlled by the shared provider policy [USE_GLOBAL_PROVIDER_CACHE]:
 * - `true`: all [UserStorageProvider] instances share one cache map.
 * - `false`: each [UserStorageProvider] instance owns a private cache map.
 */
public abstract class UserStorageProvider {
    private companion object {
        val sharedUserStorageCache: ConcurrentMutableMap<UserId, UserStorage> = ConcurrentMutableMap()
    }

    private val providerUserStorageCache: ConcurrentMutableMap<UserId, UserStorage> =
        if (USE_GLOBAL_PROVIDER_CACHE) sharedUserStorageCache else ConcurrentMutableMap()

    public fun get(userId: UserId): UserStorage? = providerUserStorageCache[userId]

    public fun getOrCreate(
        userId: UserId,
        platformUserStorageProperties: PlatformUserStorageProperties,
        shouldEncryptData: Boolean = true,
        dbInvalidationControlEnabled: Boolean,
    ): UserStorage = providerUserStorageCache.computeIfAbsent(userId) {
        create(userId, shouldEncryptData, platformUserStorageProperties, dbInvalidationControlEnabled)
    }

    protected abstract fun create(
        userId: UserId,
        shouldEncryptData: Boolean,
        platformProperties: PlatformUserStorageProperties,
        dbInvalidationControlEnabled: Boolean
    ): UserStorage

    public fun remove(userId: UserId): UserStorage? = providerUserStorageCache.remove(userId)
}

public expect class PlatformUserStorageProperties
