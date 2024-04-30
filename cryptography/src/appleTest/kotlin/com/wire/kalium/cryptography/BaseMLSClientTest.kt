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

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.URLByAppendingPathComponent

actual open class BaseMLSClientTest actual constructor() {
    actual suspend fun createMLSClient(
        clientId: CryptoQualifiedClientId,
        allowedCipherSuites: List<UShort>,
        defaultCipherSuite: UShort
    ): MLSClient {
        return createCoreCrypto(clientId, allowedCipherSuites, defaultCipherSuite).mlsClient(clientId)
    }

    actual suspend fun createCoreCrypto(
        clientId: CryptoQualifiedClientId,
        allowedCipherSuites: List<UShort>,
        defaultCipherSuite: UShort
    ): CoreCryptoCentral {
        val rootDir = NSURL.fileURLWithPath(NSTemporaryDirectory() + "/mls", isDirectory = true)
        NSFileManager.defaultManager.createDirectoryAtURL(rootDir, true, null, null)
        val keyStore = rootDir.URLByAppendingPathComponent("keystore-$clientId")!!
        return coreCryptoCentral(keyStore.path!!, "test", allowedCipherSuites, defaultCipherSuite)
    }
}
