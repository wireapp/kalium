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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.messaging.sending.SendingSession

/** The app's session for the sender: live means the app's sync manager reached live. */
internal class SyncedSendingSession(
    private val syncManager: SyncManager,
    private val transactionProvider: CryptoTransactionProvider,
) : SendingSession {

    override suspend fun waitUntilLive() = syncManager.waitUntilLive()

    override suspend fun waitUntilLiveOrFailure(): Either<CoreFailure, Unit> = syncManager.waitUntilLiveOrFailure()

    override suspend fun <R> transaction(
        name: String,
        block: suspend (transactionContext: CryptoTransactionContext) -> Either<CoreFailure, R>
    ): Either<CoreFailure, R> = transactionProvider.transaction(name, block)
}
