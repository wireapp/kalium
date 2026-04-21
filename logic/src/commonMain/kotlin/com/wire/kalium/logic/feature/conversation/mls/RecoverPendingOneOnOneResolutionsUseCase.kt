/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.mls.PendingActionsRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import io.mockative.Mockable
import kotlinx.coroutines.flow.first

@Mockable
internal interface RecoverPendingOneOnOneResolutionsUseCase {
    suspend operator fun invoke()
}

internal class RecoverPendingOneOnOneResolutionsUseCaseImpl(
    private val pendingActionsRepository: PendingActionsRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val transactionProvider: CryptoTransactionProvider,
    private val oneOnOneResolver: OneOnOneResolver,
) : RecoverPendingOneOnOneResolutionsUseCase {

    override suspend fun invoke() {
        if (incrementalSyncRepository.incrementalSyncState.first() !is IncrementalSyncStatus.Live) {
            return
        }

        val pendingUserIds = pendingActionsRepository.getPendingOneOnOneResolutions()
        if (pendingUserIds.isEmpty()) return

        val successfulRecoveries = transactionProvider.transaction("recoverPendingOneOnOneResolutions") { transactionContext ->
            Either.Right(recoverPendingUsers(transactionContext, pendingUserIds))
        }

        when (successfulRecoveries) {
            is Either.Left -> Unit
            is Either.Right -> {
                if (successfulRecoveries.value.isNotEmpty()) {
                    pendingActionsRepository.acknowledgePendingOneOnOneResolutions(successfulRecoveries.value)
                }
            }
        }
    }

    private suspend fun recoverPendingUsers(
        transactionContext: CryptoTransactionContext,
        pendingUserIds: List<UserId>
    ): List<UserId> {
        val successfulUserIds = mutableListOf<UserId>()
        pendingUserIds.forEach { userId ->
            when (
                oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                    transactionContext = transactionContext,
                    userId = userId,
                    invalidateCurrentKnownProtocols = true
                ).onFailure {
                    kaliumLogger.w("Failed to recover pending one-on-one resolution for ${userId.toLogString()}: $it")
                }
            ) {
                is Either.Left -> Unit
                is Either.Right -> successfulUserIds.add(userId)
            }
        }
        return successfulUserIds
    }
}
