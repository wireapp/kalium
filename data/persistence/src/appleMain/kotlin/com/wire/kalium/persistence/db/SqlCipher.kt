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

/**
 * Keys a connection with SQLCipher. [key] is SQLCipher's raw-key form, `x'<64 hex digits>'`, as `SecurityHelper` creates
 * it, so SQLCipher skips its key derivation.
 */
internal fun DatabaseConnection.setSqlCipherKey(key: String) {
    requireSqlCipher()
    setCipherKey(key)
}

/**
 * Kalium doesn't link SQLCipher on Apple. CoreCrypto's library contains it and exports the SQLite API, and every app
 * using Kalium links CoreCrypto, so the SQLite that SQLiter calls is SQLCipher. Plain SQLite would accept `PRAGMA key`
 * and write the database unencrypted, so a connection whose SQLite isn't SQLCipher is refused.
 */
internal fun DatabaseConnection.requireSqlCipher() {
    val cipherVersion = withStatement("PRAGMA cipher_version") {
        val cursor = query()
        if (cursor.next()) cursor.getString(0) else null
    }
    checkNotNull(cipherVersion) {
        "The SQLite in this process is not SQLCipher, so the database can't be encrypted. " +
            "On Apple, SQLCipher comes with CoreCrypto's library."
    }
}
