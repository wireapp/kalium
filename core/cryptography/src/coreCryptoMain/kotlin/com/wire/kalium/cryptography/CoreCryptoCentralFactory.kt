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
@file:Suppress("TooGenericExceptionCaught")

package com.wire.kalium.cryptography

import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoLogLevel
import com.wire.crypto.Database
import com.wire.crypto.DatabaseKey
import com.wire.crypto.open
import com.wire.crypto.setLogger
import com.wire.crypto.setMaxLogLevel

actual suspend fun coreCryptoCentral(
    rootDir: String,
    passphrase: ByteArray,
): CoreCryptoCentral {
    val path = "$rootDir/${CoreCryptoCentralImpl.KEYSTORE_NAME}"
    createDirectory(rootDir)

    val databaseKey = DatabaseKey(passphrase)
    val database = try {
        Database.open(path, databaseKey)
    } finally {
        databaseKey.close()
    }

    val coreCrypto = try {
        setLogger(CoreCryptoLoggerImpl)
        setMaxLogLevel(CoreCryptoLogLevel.WARN)
        CoreCrypto(database)
    } catch (exception: Exception) {
        database.close()
        throw exception
    }

    return CoreCryptoCentralImpl(
        cc = coreCrypto,
        rootDir = rootDir,
        database = database
    )
}
