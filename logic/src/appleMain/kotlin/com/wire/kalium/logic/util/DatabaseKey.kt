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

package com.wire.kalium.logic.util

import platform.Foundation.NSFileManager

/**
 * The key of the Apple database at [databasePath].
 *
 * Apple databases were never encrypted before, so there is no legacy key to migrate from: a database that doesn't
 * exist yet gets [newKey], the stored key or a new one, which the Keychain keeps consistent across the processes
 * sharing it. Every process stores the key before it creates the database, so an existing database has a key, and
 * [existingKey] has to return it: a new key couldn't open the database, so it fails instead of storing one.
 */
internal fun <T : Any> databaseKey(databasePath: String, existingKey: () -> T?, newKey: () -> T): T =
    if (NSFileManager.defaultManager.fileExistsAtPath(databasePath)) {
        checkNotNull(existingKey()) { "The database exists, but its key is missing from the Keychain" }
    } else {
        newKey()
    }
