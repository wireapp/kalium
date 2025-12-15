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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import com.wire.kalium.util.string.toHexString
import kotlinx.coroutines.withContext

/**
 * Use case to recover from an invalid removal key state on all MLS conversations.
 * This is an experimental feature to be trigger manually via debug options for now.
 */
interface RepairFaultRemovalKeysUseCase {
    suspend operator fun invoke(param: TargetedRepairParam): Either<CoreFailure, RepairResult>
}

internal class RepairFaultRemovalKeysUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val resetMLSConversation: ResetMLSConversationUseCase,
    private val transactionProvider: CryptoTransactionProvider,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) : RepairFaultRemovalKeysUseCase {

    override suspend fun invoke(param: TargetedRepairParam): Either<CoreFailure, RepairResult> = withContext(dispatcher.io) {
        conversationRepository.getMLSConversationsByDomain(param.domain).flatMap { conversations ->
            val conversationsWithFaultyKeys = conversations.filter { convo ->
                // check: is this equivalent to ${convo.mlsPublicKeys.removal.keys} ? instead of getting from core crypto?
                checkConversationHasFaultyKey(convo.protocol, param.faultyKey)
            }

            if (conversationsWithFaultyKeys.isEmpty()) {
                RepairResult(
                    totalConversationsChecked = conversations.size,
                    conversationsWithFaultyKeys = 0,
                    successfullyRepairedConversations = 0,
                    failedRepairs = emptyList()
                ).right()
            } else {
                repairConversations(conversationsWithFaultyKeys, conversations.size)
            }
        }
    }

    private suspend fun repairConversations(
        conversationsToRepair: List<Conversation>,
        totalChecked: Int
    ): Either<CoreFailure, RepairResult> {
        val failedRepairs = mutableListOf<String>()
        var successfulRepairs = 0
        conversationsToRepair.forEach { convo ->
            delegateResetMLSConversation(convo.id).fold(
                { failedRepairs.add(convo.id.toLogString()) },
                { successfulRepairs++ }
            )
        }

        return RepairResult(
            totalConversationsChecked = totalChecked,
            conversationsWithFaultyKeys = conversationsToRepair.size,
            successfullyRepairedConversations = successfulRepairs,
            failedRepairs = failedRepairs
        ).right()
    }

    private suspend fun delegateResetMLSConversation(conversationId: ConversationId): Either<CoreFailure, Unit> {
        return transactionProvider.transaction("RepairFaultRemovalKeys") { context ->
            resetMLSConversation(conversationId, context)
        }
    }

    private suspend fun checkConversationHasFaultyKey(protocolInfo: Conversation.ProtocolInfo, faultyKey: String): Boolean {
        return when (protocolInfo) {
            Conversation.ProtocolInfo.Proteus -> false
            is Conversation.ProtocolInfo.MLS,
            is Conversation.ProtocolInfo.Mixed -> {
                transactionProvider.mlsTransaction<Boolean>("CheckFaultyRemovalKey") { context ->
                    wrapMLSRequest {
                        context.getExternalSenders(protocolInfo.groupId.value).value.toHexString() == faultyKey
                    }
                }.fold(
                    {
                        kaliumLogger.w("Skipping faulty key check for conversation ${protocolInfo.groupId.toLogString()} due to error")
                        false
                    },
                    { it }
                )
            }
        }
    }
}

/**
 * Parameters for targeted repair of faulty removal keys in MLS conversations.
 * @property faultyKey The faulty removal key to be repaired in hex string format.
 */
data class TargetedRepairParam(
    val domain: String,
    val faultyKey: String
)

/**
 * Result of the repair operation.
 * @property totalConversationsChecked Total number of conversations checked in the domain.
 * @property conversationsWithFaultyKeys Number of conversations that had the faulty key.
 * @property successfullyRepairedConversations Number of conversations that were successfully repaired.
 * @property failedRepairs List of conversation IDs where repair failed.
 */
data class RepairResult(
    val totalConversationsChecked: Int,
    val conversationsWithFaultyKeys: Int,
    val successfullyRepairedConversations: Int,
    val failedRepairs: List<String>
)
