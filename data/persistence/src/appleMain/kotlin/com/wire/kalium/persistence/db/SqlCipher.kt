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
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The first bytes of an encrypted database that stay unencrypted, see [setSqlCipherKey]. 32 is what SQLCipher
 * recommends for this.
 */
internal const val PLAINTEXT_HEADER_SIZE = 32

/**
 * Turns SQLCipher's raw-key form, `x'<64 hex digits>'` as `SecurityHelper` creates it, into the raw-key form with an
 * explicit salt, `x'<64 hex digits key><32 hex digits salt>'`, which [setSqlCipherKey] needs.
 *
 * The salt is derived from the key, as the first 16 bytes of SHA-256 of [SALT_DERIVATION_LABEL] and the key bytes, and
 * stored nowhere, so every process that has the key opens the database without coordinating with the others. Every
 * database has its own random key, so every database gets its own salt.
 *
 * The derivation is part of the database format: changing it, or rekeying a database without the salt of the new key,
 * makes existing databases unreadable.
 */
internal fun sqlCipherKeyWithSalt(rawKey: String): String {
    require(rawKey.length == RAW_KEY_LENGTH && rawKey.startsWith("x'") && rawKey.endsWith("'")) {
        "Expected a SQLCipher raw key, x'<64 hex digits>'"
    }
    val keyHex = rawKey.substring(2, rawKey.length - 1)
    val salt = sha256(SALT_DERIVATION_LABEL.encodeToByteArray() + keyHex.hexToByteArray()).copyOf(SALT_SIZE)
    return "x'$keyHex${salt.toHexString()}'"
}

/**
 * Keys a connection with [keyWithSalt], see [sqlCipherKeyWithSalt], and keeps the first [PLAINTEXT_HEADER_SIZE] bytes
 * of the database unencrypted.
 *
 * iOS terminates a suspended app that holds a lock on a file in a shared (App Group) container, unless it recognizes
 * the file by its header as a SQLite database in WAL mode. SQLCipher encrypts that header, where it otherwise keeps the
 * salt, so the header stays readable and the salt comes with the key. Both have to be set on every connection before it
 * reads the database: https://www.zetetic.net/sqlcipher/sqlcipher-api/#cipher_plaintext_header_size
 */
internal fun DatabaseConnection.setSqlCipherKey(keyWithSalt: String) {
    requireSqlCipher()
    setCipherKey(keyWithSalt)
    rawExecSql("PRAGMA cipher_plaintext_header_size = $PLAINTEXT_HEADER_SIZE")
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

private fun sha256(data: ByteArray): ByteArray {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    data.usePinned { input ->
        digest.usePinned { output -> CC_SHA256(input.addressOf(0), data.size.convert(), output.addressOf(0)) }
    }
    return digest.asByteArray()
}

private const val SALT_DERIVATION_LABEL = "kalium-sqlcipher-salt-v1"
private const val SALT_SIZE = 16

/** `x'`, 64 hex digits and `'`. */
private const val RAW_KEY_LENGTH = 67
