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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationsUseCase

/**
 * Recovers the self user's MLS conversations after synchronization is live.
 * Established groups are checked first, then pending groups are joined in a fresh transaction.
 */
public fun interface RecoverMLSConversationsForUserUseCase {
    public suspend operator fun invoke(): RecoverMLSConversationsForUserResult
}

public sealed interface RecoverMLSConversationsForUserResult {
    public data object Success : RecoverMLSConversationsForUserResult
    public data class Failure(public val cause: CoreFailure) : RecoverMLSConversationsForUserResult
}

internal class RecoverMLSConversationsForUserUseCaseImpl(
    private val recoverEstablishedMLSConversations: RecoverMLSConversationsUseCase,
    private val joinPendingMLSConversations: JoinExistingMLSConversationsUseCase,
    private val transactionProvider: CryptoTransactionProvider,
) : RecoverMLSConversationsForUserUseCase {

    override suspend fun invoke(): RecoverMLSConversationsForUserResult {
        val establishedRecovery = transactionProvider.transaction("RecoverEstablishedMLSConversations") { transactionContext ->
            when (val result = recoverEstablishedMLSConversations(transactionContext)) {
                RecoverMLSConversationsResult.Success -> Either.Right(Unit)
                is RecoverMLSConversationsResult.Failure -> Either.Left(result.failure)
            }
        }

        return establishedRecovery.fold(
            { RecoverMLSConversationsForUserResult.Failure(it) },
            {
                joinPendingMLSConversations(
                    keepRetryingOnFailure = true,
                    allowJoinByExternalCommit = true,
                ).fold(
                    { RecoverMLSConversationsForUserResult.Failure(it) },
                    { RecoverMLSConversationsForUserResult.Success },
                )
            },
        )
    }
}
