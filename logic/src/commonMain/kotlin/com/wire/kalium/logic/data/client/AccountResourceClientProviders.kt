/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.util.AccountResourceAccess
import com.wire.kalium.util.InternalCryptoAccess

/** Admit before taking provider mutexes, so handoff never waits on a caller queued behind it. */
@OptIn(InternalCryptoAccess::class)
internal fun ProteusClientProvider.guardedBy(access: AccountResourceAccess?): ProteusClientProvider {
    if (access == null) return this
    val provider = this
    return object : ProteusClientProvider {
        override suspend fun clearLocalFiles() = access.withAccess { provider.clearLocalFiles() }
        override suspend fun close() = access.withAccess { provider.close() }
        override suspend fun getOrCreate() = access.withAccess { provider.getOrCreate() }
        override suspend fun getOrError() = access.withAccess { provider.getOrError() }
        override suspend fun exportCryptoDB(exportPath: String) = access.withAccess { provider.exportCryptoDB(exportPath) }
    }
}

internal fun MLSClientProvider.guardedBy(access: AccountResourceAccess?): MLSClientProvider {
    if (access == null) return this
    val provider = this
    return object : MLSClientProvider {
        override suspend fun isMLSClientInitialised() = access.withAccess { provider.isMLSClientInitialised() }
        override suspend fun getMLSClient(clientId: ClientId?) = access.withAccess { provider.getMLSClient(clientId) }
        override suspend fun getCoreCrypto(clientId: ClientId?) = access.withAccess { provider.getCoreCrypto(clientId) }
        override suspend fun clearLocalFiles() = access.withAccess { provider.clearLocalFiles() }
        override suspend fun close() = access.withAccess { provider.close() }
        override suspend fun getOrFetchMLSConfig() = access.withAccess { provider.getOrFetchMLSConfig() }
        override suspend fun exportCryptoDB(exportPath: String) = access.withAccess { provider.exportCryptoDB(exportPath) }
    }
}
