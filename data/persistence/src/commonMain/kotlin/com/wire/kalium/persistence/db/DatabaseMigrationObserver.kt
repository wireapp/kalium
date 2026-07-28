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

package com.wire.kalium.persistence.db

/**
 * Reports synchronous schema migration boundaries while a database is being opened.
 *
 * Implementations must return quickly and must not perform database work.
 */
public interface DatabaseMigrationObserver {
    public fun onMigrationStarted(fromVersion: Long, toVersion: Long)

    public fun onMigrationCompleted(fromVersion: Long, toVersion: Long)

    public companion object {
        public val None: DatabaseMigrationObserver = object : DatabaseMigrationObserver {
            override fun onMigrationStarted(fromVersion: Long, toVersion: Long) = Unit

            override fun onMigrationCompleted(fromVersion: Long, toVersion: Long) = Unit
        }
    }
}
