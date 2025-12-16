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
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import com.wire.kalium.util.string.toHexString
import kotlinx.coroutines.withContext

/**
 * Use case to recover from an invalid removal key state on all MLS conversations.
 * This is an experimental feature to be trigger manually via debug options for now.
 */
interface RepairFaultRemovalKeysUseCase {
    suspend operator fun invoke(param: TargetedRepairParam): RepairResult
}

internal class RepairFaultRemovalKeysUseCaseImpl(
    private val selfUserId: UserId,
    private val conversationRepository: ConversationRepository,
    private val resetMLSConversation: ResetMLSConversationUseCase,
    private val transactionProvider: CryptoTransactionProvider,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) : RepairFaultRemovalKeysUseCase {

    val logger by lazy { kaliumLogger.withTextTag("RepairFaultRemovalKeysUseCase") }

    override suspend fun invoke(param: TargetedRepairParam): RepairResult = withContext(dispatcher.io) {
        if (selfUserId.domain != param.domain) {
            logger.w("Attempted to repair faulty removal keys in domain ${param.domain}, but user belongs to ${selfUserId.domain}. Aborting.")
            return@withContext RepairResult.RepairNotNeeded
        }

        conversationRepository.getMLSConversationsByDomain(param.domain)
            .flatMap { conversations ->
                val conversationsWithFaultyKeys = conversations.filter { convo ->
                    checkConversationHasFaultyKey(convo.protocol, param.faultyKey)
                }

                when {
                    conversationsWithFaultyKeys.isEmpty() -> RepairResult.NoConversationsToRepair.right()
                    else -> repairConversations(conversationsWithFaultyKeys, conversations.size).right()
                }
            }
            .fold(
                fnL = {
                    logger.e("Error occurred during repair of faulty removal keys")
                    RepairResult.Error
                },
                fnR = { it }
            )
    }

    private suspend fun repairConversations(
        conversationsToRepair: List<Conversation>,
        totalChecked: Int
    ): RepairResult {
        val (successful, failed) = conversationsToRepair
            .map { convo -> convo to delegateResetMLSConversation(convo.id) }
            .partition { (_, result) -> result.isRight() }

        return RepairResult.RepairPerformed(
            totalConversationsChecked = totalChecked,
            conversationsWithFaultyKeys = conversationsToRepair.size,
            successfullyRepairedConversations = successful.size,
            failedRepairs = failed.map { (convo, _) -> convo.id.toLogString() }
        )
    }

    private suspend fun delegateResetMLSConversation(conversationId: ConversationId): Either<CoreFailure, Unit> = try {
        transactionProvider.transaction("RepairFaultRemovalKeys") { context ->
            resetMLSConversation(conversationId, context)
        }
    } catch (exception: Exception) {
        logger.e("Exception during resetting MLS conversation ${conversationId.toLogString()}", exception)
        CoreFailure.Unknown(exception).left()
    }

    private suspend fun checkConversationHasFaultyKey(protocolInfo: Conversation.ProtocolInfo, faultyKey: String): Boolean =
        when (protocolInfo) {
            Conversation.ProtocolInfo.Proteus -> false
            is Conversation.ProtocolInfo.MLS,
            is Conversation.ProtocolInfo.Mixed -> {
                transactionProvider.mlsTransaction<Boolean>("CheckFaultyRemovalKey") { context ->
                    wrapMLSRequest {
                        context.getExternalSenders(protocolInfo.groupId.value).value.toHexString() == faultyKey
                    }
                }.fold(
                    fnL = {
                        logger.w("Skipping faulty key check for conversation ${protocolInfo.groupId.toLogString()} due to error")
                        false
                    },
                    fnR = { it }
                )
            }
        }
}

/**
 * Parameters for targeted repair of faulty removal keys in MLS conversations.
 * @property faultyKey The faulty removal key to be repaired in hex string format.
 * @property domain The domain in which the user and conversations belongs.
 */
data class TargetedRepairParam(
    val domain: String,
    val faultyKey: String
)

/**
 * Result of the repair operation for faulty removal keys.
 */
sealed interface RepairResult {
    data object Error : RepairResult
    data object RepairNotNeeded : RepairResult
    data object NoConversationsToRepair : RepairResult

    /**
     * Result of the repair operation.
     * @property totalConversationsChecked Total number of conversations checked in the domain.
     * @property conversationsWithFaultyKeys Number of conversations that had the faulty key.
     * @property successfullyRepairedConversations Number of conversations that were successfully repaired.
     * @property failedRepairs List of conversation IDs where repair failed.
     */
    data class RepairPerformed(
        val totalConversationsChecked: Int,
        val conversationsWithFaultyKeys: Int,
        val successfullyRepairedConversations: Int,
        val failedRepairs: List<String>
    ) : RepairResult
}
