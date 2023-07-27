/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.crypto.ClientId
import com.wire.crypto.ConversationId
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoCallbacks
import platform.Foundation.NSFileManager

actual suspend fun coreCryptoCentral(rootDir: String, databaseKey: String): CoreCryptoCentral {
    val path = "$rootDir/${CoreCryptoCentralImpl.KEYSTORE_NAME}"
    NSFileManager.defaultManager.createDirectoryAtPath(rootDir, withIntermediateDirectories = true, null, null)
    val coreCrypto = CoreCrypto.deferredInit(path, databaseKey, null)
    coreCrypto.setCallbacks(Callbacks())
    return CoreCryptoCentralImpl(coreCrypto, rootDir)
}

private class Callbacks : CoreCryptoCallbacks {

    override fun authorize(conversationId: ConversationId, clientId: ClientId): Boolean {
        // We always return true because our BE is currently enforcing that this constraint is always true
        return true
    }

    override fun clientIsExistingGroupUser(clientId: ClientId, existingClients: List<ClientId>): Boolean {
        // We always return true because our BE is currently enforcing that this constraint is always true
        return true
    }

    override fun userAuthorize(
        conversationId: ConversationId,
        externalClientId: ClientId,
        existingClients: List<ClientId>
    ): Boolean {
        // We always return true because our BE is currently enforcing that this constraint is always true
        return true
    }
}

class CoreCryptoCentralImpl(private val cc: CoreCrypto, private val rootDir: String) : CoreCryptoCentral {

    override suspend fun mlsClient(clientId: CryptoQualifiedClientId): MLSClient {
//         val coreCrypto = CoreCrypto(rootDir, "databaseKey", MLSClientImpl.toUByteList(clientId.value),  null)
//         coreCrypto.setCallbacks(Callbacks())
        cc.mlsInit(MLSClientImpl.toUByteList(clientId.value))
        return MLSClientImpl(cc)
    }

    override suspend fun proteusClient(): ProteusClient {
//         NSFileManager.defaultManager.createDirectoryAtPath(rootDir, withIntermediateDirectories = true, null, null)
//         val coreCrypto = CoreCrypto(rootDir, "databaseKey", MLSClientImpl.toUByteList("clientId.value"),  null)
//         coreCrypto.setCallbacks(Callbacks())
        return ProteusClientCoreCryptoImpl(cc, rootDir)
    }

    companion object {
        const val KEYSTORE_NAME = "keystore"
    }
}
