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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs

/**
 * Handles a message of [conversationId] that failed its signature check, which happens for every proposal of the
 * hosting backend when the group's external sender is not that backend's removal key.
 * When the self domain hosts the conversation and the group has the wrong external sender, the group is reset, so it
 * is established again with the right key.
 *
 * @return true if the group was reset, so the failure isn't a message the user missed. Otherwise the group may stay
 * broken, and the failure is shown.
 */
internal interface HandleWrongExternalSenderUseCase {
    suspend operator fun invoke(transactionContext: CryptoTransactionContext, conversationId: ConversationId): Boolean
}

internal class HandleWrongExternalSenderUseCaseImpl(
    private val selfUserId: UserId,
    private val userConfig: UserConfigRepository,
    private val kaliumConfigs: KaliumConfigs,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val resetMLSConversation: ResetMLSConversationUseCase,
) : HandleWrongExternalSenderUseCase {

    private val logger by lazy { kaliumLogger.withTextTag("HandleWrongExternalSenderUseCase") }

    override suspend fun invoke(transactionContext: CryptoTransactionContext, conversationId: ConversationId): Boolean =
        // Only groups of the self domain can be reset, and only the self backend's removal key is known.
        conversationId.domain == selfUserId.domain && isResetEnabled() &&
            hasWrongExternalSender(transactionContext, conversationId) && reset(transactionContext, conversationId)

    // The reset use case reports success without resetting when the reset is disabled.
    private suspend fun isResetEnabled() =
        kaliumConfigs.isMlsResetEnabled && userConfig.isMlsConversationsResetEnabled()

    private suspend fun hasWrongExternalSender(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId
    ): Boolean {
        val mlsContext = transactionContext.mls
        val protocolInfo = conversationRepository.getConversationProtocolInfo(conversationId).getOrNull()
            as? Conversation.ProtocolInfo.MLSCapable
        return mlsContext != null && protocolInfo != null && mlsConversationRepository.hasWrongExternalSender(
            mlsContext = mlsContext,
            groupID = protocolInfo.groupId,
            cipherSuite = protocolInfo.cipherSuite
        ).getOrElse { false }
    }

    private suspend fun reset(transactionContext: CryptoTransactionContext, conversationId: ConversationId): Boolean {
        logger.w("Resetting ${conversationId.toLogString()}, its external sender is not this backend's removal key")
        val result = resetMLSConversation(conversationId, transactionContext)
        if (result is ResetMLSConversationResult.Failure) {
            logger.e("Failed to reset ${conversationId.toLogString()}: ${result.error}")
        }
        return result is ResetMLSConversationResult.Success
    }
}
