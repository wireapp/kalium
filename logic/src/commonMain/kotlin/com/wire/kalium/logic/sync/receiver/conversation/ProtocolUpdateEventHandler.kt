/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationSyncReason
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.util.ConversationPersistenceApi
import io.mockative.Mockable
import kotlinx.coroutines.flow.first

@Mockable
internal interface ProtocolUpdateEventHandler {
    suspend fun handle(
        transactionContext: CryptoTransactionContext,
        event: Event.Conversation.ConversationProtocol
    ): Either<CoreFailure, Unit>
}

@OptIn(ConversationPersistenceApi::class)
internal class ProtocolUpdateEventHandlerImpl(
    private val systemMessageInserter: SystemMessageInserter,
    private val callRepository: CallRepository,
    private val conversationRepository: ConversationRepository,
    private val persistConversations: PersistConversationsUseCase,
    private val slowSyncRepository: SlowSyncRepository
) : ProtocolUpdateEventHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(
        transactionContext: CryptoTransactionContext,
        event: Event.Conversation.ConversationProtocol
    ): Either<CoreFailure, Unit> {
        val eventLogger = logger.createEventProcessingLogger(event)
        // Fetch the latest conversation details from backend and persist with proper group state
        return conversationRepository.fetchConversation(event.conversationId)
            .flatMap { conversationResponse ->
                // Persist conversation with ConversationSyncReason.Other to set PENDING_JOIN for MLS groups
                persistConversations(
                    transactionContext,
                    listOf(conversationResponse),
                    invalidateMembers = true,
                    reason = ConversationSyncReason.Other
                ).map { true }
            }
            .flatMap { updated ->
                // When conversation protocol changes to MLS or MIXED, signal that MLS groups need to be joined
                if (event.protocol == Conversation.Protocol.MLS || event.protocol == Conversation.Protocol.MIXED) {
                    slowSyncRepository.setNeedsToRecoverMLSGroups(true)
                }
                Either.Right(updated)
            }
            .onSuccess { updated ->
                if (updated) {
                    systemMessageInserter.insertProtocolChangedSystemMessage(
                        event.conversationId,
                        event.senderUserId,
                        event.protocol
                    )
                    if (callRepository.establishedCallsFlow().first().isNotEmpty() &&
                        event.protocol == Conversation.Protocol.MIXED
                    ) {
                        systemMessageInserter.insertProtocolChangedDuringACallSystemMessage(
                            event.conversationId,
                            event.senderUserId
                        )
                    }
                }
                eventLogger.logSuccess()
            }
            .onFailure { eventLogger.logFailure(it) }
            .map { }
    }
}
