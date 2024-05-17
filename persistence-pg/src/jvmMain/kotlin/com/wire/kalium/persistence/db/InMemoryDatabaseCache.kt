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
package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao.UserIDEntity

/**
 * Provides an in-memory cache for in-memory databases.
 *
 * Useful to hold a database across logins and logouts without losing the data in between.
 */
internal object InMemoryDatabaseCache {
    private val cache = mutableMapOf<UserIDEntity, UserDatabaseBuilder>()

    fun getOrCreate(
        userIDEntity: UserIDEntity,
        create: () -> UserDatabaseBuilder
    ): UserDatabaseBuilder {
        return cache.getOrPut(userIDEntity) { create() }
    }

    /**
     * Clears an entry from the cache based on the provided [userIDEntity].
     *
     * @param userIDEntity The [UserIDEntity] representing the entry to be cleared.
     * @return `true` if the entry was successfully cleared, `false` if it didn't exist.
     */
    fun clearEntry(userIDEntity: UserIDEntity): Boolean {
        return cache.remove(userIDEntity) != null
    }
}
