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

import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.setCipherKey
import co.touchlab.sqliter.withStatement
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.OsFamily
import kotlin.native.Platform

/**
 * Whether encrypted databases keep their first [PLAINTEXT_HEADER_SIZE] bytes readable, see [setSqlCipherKey]. Only
 * iOS needs it; on macOS the whole database is encrypted, with the salt in its header, as SQLCipher does by default.
 * It is part of the database format, so it depends on nothing but the platform.
 */
@OptIn(ExperimentalNativeApi::class)
internal val isPlaintextHeaderUsed: Boolean = Platform.osFamily == OsFamily.IOS

/**
 * The first bytes of an encrypted iOS database that stay unencrypted, see [setSqlCipherKey]. 32 is what SQLCipher
 * recommends for this.
 */
internal const val PLAINTEXT_HEADER_SIZE = 32

/**
 * Checks that [key] can encrypt a database on this platform. On iOS, it has to be SQLCipher's raw-key form with the
 * salt, `x'<64 hex digits key><32 hex digits salt>'`, as `SecurityHelper` creates it there from the key and the salt
 * it stores next to it. With the readable header, SQLCipher has no place to keep a salt of its own: it would encrypt a
 * new database with a random one, and lose it.
 */
internal fun requireSqlCipherKey(key: String) {
    if (isPlaintextHeaderUsed) {
        require(RAW_KEY_WITH_SALT.matches(key)) { "On iOS, the database key has to be a SQLCipher raw key with its salt" }
    }
}

/**
 * Keys a connection with [key], see [requireSqlCipherKey]. On iOS, also keeps the first [PLAINTEXT_HEADER_SIZE] bytes
 * of the database unencrypted.
 *
 * iOS terminates a suspended app that holds a lock on a file in a shared (App Group) container, unless it recognizes
 * the file by its header as a SQLite database in WAL mode. SQLCipher encrypts that header, where it otherwise keeps the
 * salt, so the header stays readable and the salt comes with the key. Both have to be set on every connection before it
 * reads the database: https://www.zetetic.net/sqlcipher/sqlcipher-api/#cipher_plaintext_header_size
 */
internal fun DatabaseConnection.setSqlCipherKey(key: String) {
    requireSqlCipher()
    setCipherKey(key)
    if (isPlaintextHeaderUsed) rawExecSql("PRAGMA cipher_plaintext_header_size = $PLAINTEXT_HEADER_SIZE")
}

/**
 * Kalium doesn't link SQLCipher on Apple. CoreCrypto's library contains it and exports the SQLite API, and every app
 * using Kalium links CoreCrypto, so the SQLite that SQLiter calls is SQLCipher. Plain SQLite would accept `PRAGMA key`
 * and write the database unencrypted, so a connection whose SQLite isn't SQLCipher is refused.
 *
 * The SQLite a process calls doesn't change, so once it was confirmed to be SQLCipher, it isn't checked again.
 */
@OptIn(ExperimentalAtomicApi::class)
internal fun DatabaseConnection.requireSqlCipher() {
    if (isSqlCipherConfirmed.load()) return
    val cipherVersion = withStatement("PRAGMA cipher_version") {
        val cursor = query()
        if (cursor.next()) cursor.getString(0) else null
    }
    checkNotNull(cipherVersion) {
        "The SQLite in this process is not SQLCipher, so the database can't be encrypted. " +
            "On Apple, SQLCipher comes with CoreCrypto's library."
    }
    isSqlCipherConfirmed.store(true)
}

@OptIn(ExperimentalAtomicApi::class)
private val isSqlCipherConfirmed = AtomicBoolean(false)

/** `x'`, 64 hex digits of key, 32 hex digits of salt and `'`. */
private val RAW_KEY_WITH_SALT = Regex("x'[0-9a-fA-F]{96}'")
