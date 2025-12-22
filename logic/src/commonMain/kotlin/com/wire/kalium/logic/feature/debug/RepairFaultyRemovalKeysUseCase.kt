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
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import com.wire.kalium.util.string.toHexString
import io.mockative.Mockable
import kotlinx.coroutines.withContext

/**
 * Use case to recover from an invalid removal key state on all MLS conversations.
 * This is an experimental feature to be trigger manually via debug options for now.
 */
@Mockable
interface RepairFaultyRemovalKeysUseCase {
    suspend operator fun invoke(param: TargetedRepairParam): RepairResult
}

internal class RepairFaultyRemovalKeysUseCaseImpl(
    private val selfUserId: UserId,
    private val conversationRepository: ConversationRepository,
    private val resetMLSConversation: ResetMLSConversationUseCase,
    private val transactionProvider: CryptoTransactionProvider,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) : RepairFaultyRemovalKeysUseCase {

    val logger by lazy { kaliumLogger.withTextTag("RepairFaultyRemovalKeysUseCase") }

    override suspend fun invoke(param: TargetedRepairParam): RepairResult = withContext(dispatcher.io) {
        if (selfUserId.domain != param.domain) {
            logger.w("Aborting repair faulty removal keys in domain ${param.domain}, user belongs to ${selfUserId.domain}")
            return@withContext RepairResult.RepairNotNeeded
        }
        transactionProvider.transaction("RepairFaultRemovalKeys") { transactionContext ->
            // get all MLS conversations for the user's domain.
            val conversations = conversationRepository.getMLSConversationsByDomain(selfUserId.domain).getOrElse { emptyList() }
            val conversationsWithFaultyKeys = conversations.filter { convo ->
                checkConversationHasFaultyKey(convo.protocol, param.faultyKeys, transactionContext)
            }
            when {
                conversationsWithFaultyKeys.isEmpty() -> RepairResult.NoConversationsToRepair.right()
                else ->
                    repairConversations(conversationsWithFaultyKeys, conversations.size, transactionContext).right()
            }
        }.fold(
            fnL = {
                logger.e("Error occurred during repair of faulty removal keys")
                RepairResult.Error
            },
            fnR = { it }
        )
    }

    private suspend fun repairConversations(
        conversationsToRepair: List<Conversation>,
        totalChecked: Int,
        transactionContext: CryptoTransactionContext
    ): RepairResult.RepairPerformed {
        val (successful, failed) = conversationsToRepair
            .map { convo -> convo to delegateResetMLSConversation(convo.id, transactionContext) }
            .partition { (_, result) -> result.isRight() }

        return RepairResult.RepairPerformed(
            totalConversationsChecked = totalChecked,
            conversationsWithFaultyKeys = conversationsToRepair.size,
            successfullyRepairedConversations = successful.size,
            failedRepairs = failed.map { (convo, _) -> convo.id.toLogString() }
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun delegateResetMLSConversation(
        conversationId: ConversationId,
        transactionContext: CryptoTransactionContext
    ): Either<CoreFailure, Unit> = try {
        resetMLSConversation(conversationId, transactionContext)
    } catch (exception: Exception) {
        logger.e("Exception during resetting MLS conversation ${conversationId.toLogString()}", exception)
        CoreFailure.Unknown(exception).left()
    }

    private suspend fun checkConversationHasFaultyKey(
        protocolInfo: Conversation.ProtocolInfo,
        faultyKeys: List<String>,
        transactionContext: CryptoTransactionContext
    ): Boolean =
        when (protocolInfo) {
            Conversation.ProtocolInfo.Proteus -> false
            is Conversation.ProtocolInfo.MLS,
            is Conversation.ProtocolInfo.Mixed -> {
                transactionContext.wrapInMLSContext { context ->
                    wrapMLSRequest {
                        val externalHex = context.getExternalSenders(protocolInfo.groupId.value).value.toHexString()
                        faultyKeys.contains(externalHex)
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
 * @property faultyKeys The faulty removal key to be repaired in hex string format.
 * @property domain The target domain in which the repair should be performed for the user and conversations.
 */
data class TargetedRepairParam(
    val domain: String,
    val faultyKeys: List<String>
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
    ) : RepairResult {
        fun toLogString(): String =
            "TotalChecked=$totalConversationsChecked, " +
                    "WithFaultyKeys=$conversationsWithFaultyKeys, " +
                    "SuccessfullyRepaired=$successfullyRepairedConversations, " +
                    "FailedRepairs=${failedRepairs.size}"
    }
}
