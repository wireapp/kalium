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

/**
 * Provides an in-memory cache for in-memory databases.
 *
 * On iOS, SQLiter uses "file:{name}?mode=memory&cache=shared" for named in-memory databases,
 * which means databases with the same name share data. This cache allows us to:
 * 1. Reuse database instances across calls with the same name
 * 2. Properly close and clear databases between tests to ensure isolation
 *
 * When [clearEntry] is called, the database driver is closed, which causes SQLite to
 * automatically delete the shared in-memory database (since it's the last connection).
 */
internal object InMemoryDatabaseCache {
    private val cache = mutableMapOf<String, UserDatabaseBuilder>()

    fun getOrCreate(
        databaseName: String,
        create: () -> UserDatabaseBuilder
    ): UserDatabaseBuilder {
        return cache.getOrPut(databaseName) { create() }
    }

    /**
     * Clears the entry for [databaseName] from the cache.
     * This also closes the database driver, which causes SQLite to delete
     * the shared in-memory database.
     *
     * @return `true` if the entry was successfully cleared, `false` if it didn't exist.
     */
    fun clearEntry(databaseName: String): Boolean {
        val builder = cache.remove(databaseName)
        builder?.nuke()
        return builder != null
    }
}
