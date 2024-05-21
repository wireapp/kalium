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

package com.wire.kalium.cryptography

import java.nio.file.Files

actual open class BaseMLSClientTest {

<<<<<<< HEAD
    actual suspend fun createMLSClient(clientId: CryptoQualifiedClientId): MLSClient {
        return createCoreCrypto(clientId).mlsClient(clientId)
    }

    actual suspend fun createCoreCrypto(clientId: CryptoQualifiedClientId): CoreCryptoCentral {
=======
    actual suspend fun createMLSClient(
        clientId: CryptoQualifiedClientId,
        allowedCipherSuites: List<UShort>,
        defaultCipherSuite: UShort
    ): MLSClient {
        return createCoreCrypto(clientId).mlsClient(clientId, allowedCipherSuites, defaultCipherSuite)
    }

    actual suspend fun createCoreCrypto(
        clientId: CryptoQualifiedClientId
    ): CoreCryptoCentral {
>>>>>>> f8c4a14166 (feat: fetch MLS config when not available locally [WPB-8592] 🍒 (#2744))
        val root = Files.createTempDirectory("mls").toFile()
        val keyStore = root.resolve("keystore-$clientId")
        return coreCryptoCentral(keyStore.absolutePath, "test")
    }

}
