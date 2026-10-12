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

import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import platform.Foundation.NSFileManager
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform

/**
 * The [SecurityHelper] of the Apple databases. On iOS, their header stays readable instead of keeping the SQLCipher
 * salt, so the salt is stored in the Keychain next to their key, see the persistence `databaseDriver`.
 */
@OptIn(ExperimentalNativeApi::class)
internal fun databaseSecurityHelper(passphraseStorage: PassphraseStorage): SecurityHelper =
    SecurityHelperImpl(passphraseStorage, isSqlCipherSaltStored = Platform.osFamily == OsFamily.IOS)

/**
 * The key of the Apple database at [databasePath].
 *
 * Apple databases were never encrypted before, so there is no legacy key to migrate from: a database that doesn't
 * exist yet gets [newKey], the stored key or a new one, which the Keychain keeps consistent across the processes
 * sharing it. Every process stores the key, and on iOS its salt, before it creates the database, so an existing
 * database has them, and [existingKey] has to return them: a new key couldn't open the database, so it fails instead
 * of storing one.
 */
internal fun <T : Any> databaseKey(databasePath: String, existingKey: () -> T?, newKey: () -> T): T =
    if (NSFileManager.defaultManager.fileExistsAtPath(databasePath)) {
        checkNotNull(existingKey()) { "The database exists, but its key or salt is missing from the Keychain" }
    } else {
        newKey()
    }
