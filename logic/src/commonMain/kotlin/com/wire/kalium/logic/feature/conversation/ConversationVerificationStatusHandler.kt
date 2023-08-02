/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationVerificationStatus
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Notify user (by adding System message in conversation) if needed about changes of Conversation Verification status.
 */
internal interface ConversationVerificationStatusHandler {
    suspend operator fun invoke(conversation: Conversation, status: ConversationVerificationStatus)
}

internal class ConversationVerificationStatusHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: UserId,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ConversationVerificationStatusHandler {
    private val dispatcher = kaliumDispatcher.io

    override suspend fun invoke(conversation: Conversation, status: ConversationVerificationStatus): Unit = withContext(dispatcher) {
        if (shouldNotifyUser(conversation, status)) {
            val content = when (conversation.protocol) {
                is Conversation.ProtocolInfo.MLS -> MessageContent.ConversationDegradedMLS
                is Conversation.ProtocolInfo.Mixed,
                Conversation.ProtocolInfo.Proteus -> MessageContent.ConversationDegradedProteus
            }
            val conversationDegradedMessage = Message.System(
                id = uuid4().toString(),
                content = content,
                conversationId = conversation.id,
                date = DateTimeUtil.currentIsoDateTimeString(),
                senderUserId = selfUserId,
                status = Message.Status.SENT,
                visibility = Message.Visibility.VISIBLE,
                expirationData = null
            )

            persistMessage(conversationDegradedMessage)
                .flatMap { conversationRepository.setInformedAboutDegradedMLSVerificationFlag(conversation.id, true) }
        } else if (status != ConversationVerificationStatus.DEGRADED) {
            conversationRepository.setInformedAboutDegradedMLSVerificationFlag(conversation.id, false)
        }
    }

    private suspend fun shouldNotifyUser(conversation: Conversation, status: ConversationVerificationStatus): Boolean =
        if (status == ConversationVerificationStatus.DEGRADED) {
            if (conversation.protocol is Conversation.ProtocolInfo.MLS) {
                !conversationRepository.isInformedAboutDegradedMLSVerification(conversation.id).getOrElse(true)
            } else {
                // TODO check flag for Proteus after implementing it.
                false
            }
        } else {
            false
        }
}
