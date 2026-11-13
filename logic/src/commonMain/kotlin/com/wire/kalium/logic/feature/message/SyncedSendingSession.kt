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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.messaging.sending.SendingSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** The app's session for the sender: live means the app's sync manager reached live. */
internal class SyncedSendingSession(
    private val syncManager: SyncManager,
    private val transactionProvider: CryptoTransactionProvider,
) : SendingSession {

    override suspend fun waitUntilLive() = syncManager.waitUntilLive()

    override suspend fun waitUntilLiveOrFailure(): Either<CoreFailure, Unit> = syncManager.waitUntilLiveOrFailure()

    override fun isLive(): Boolean = syncManager.syncState.value is SyncState.Live

    // A requested sync leaves Waiting within moments (the state can also lag its repositories). Waiting longer, or
    // again after it ran, means nothing runs sync, as after a notification reply's request times out.
    override suspend fun waitUntilLiveOrStopped(): Either<CoreFailure, Unit> {
        val started = withTimeoutOrNull(SYNC_START_TIMEOUT) { syncManager.syncState.first { it !is SyncState.Waiting } }
        val settled = started?.let {
            syncManager.syncState.first { it is SyncState.Live || it is SyncState.Failed || it is SyncState.Waiting }
        }
        return if (settled is SyncState.Live) Either.Right(Unit) else Either.Left(NetworkFailure.NoNetworkConnection(null))
    }

    override suspend fun <R> transaction(
        name: String,
        block: suspend (transactionContext: CryptoTransactionContext) -> Either<CoreFailure, R>
    ): Either<CoreFailure, R> = transactionProvider.transaction(name, block)
}

private val SYNC_START_TIMEOUT = 10.seconds
