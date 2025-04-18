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

import kotlinx.coroutines.CoroutineScope
import java.nio.file.Files

actual open class BaseMLSClientTest {
    actual suspend fun createMLSClient(
        clientId: CryptoQualifiedClientId,
        allowedCipherSuites: List<MLSCiphersuite>,
        defaultCipherSuite: MLSCiphersuite,
        mlsTransporter: MLSTransporter,
        epochObserver: MLSEpochObserver,
        coroutineScope: CoroutineScope
    ): MLSClient {
        return createCoreCrypto(clientId).mlsClient(
            clientId,
            allowedCipherSuites,
            defaultCipherSuite,
            mlsTransporter,
            epochObserver,
            coroutineScope
        )
    }

    actual suspend fun createCoreCrypto(clientId: CryptoQualifiedClientId): CoreCryptoCentral {
        val root = Files.createTempDirectory("mls").toFile()
        val keyStore = root.resolve("keystore-$clientId")
        return coreCryptoCentral(keyStore.absolutePath, "test")
    }
}
