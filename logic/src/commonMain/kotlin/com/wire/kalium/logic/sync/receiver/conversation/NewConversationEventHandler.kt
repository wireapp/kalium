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

package com.wire.kalium.logic.sync.receiver.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode as NetworkReceiptMode

interface NewConversationEventHandler {
    suspend fun handle(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit>
}

internal class NewConversationEventHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val persistMessage: PersistMessageUseCase,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val isSelfATeamMember: IsSelfATeamMemberUseCase
) : NewConversationEventHandler {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit> = conversationRepository
        .persistConversations(listOf(event.conversation), selfTeamIdProvider().getOrNull()?.value, originatedFromEvent = true)
        .flatMap { conversationRepository.updateConversationModifiedDate(event.conversationId, DateTimeUtil.currentInstant()) }
        .flatMap {
            userRepository.fetchUsersIfUnknownByIds(event.conversation.members.otherMembers.map { it.id.toModel() }
                .toSet())
        }
        .onSuccess {
            if (event.conversation.type == ConversationResponse.Type.GROUP && isSelfATeamMember()) {
                val message = Message.System(
                    uuid4().toString(),
                    MessageContent.NewConversationReceiptMode(
                        receiptMode = event.conversation.receiptMode == NetworkReceiptMode.ENABLED
                    ),
                    event.conversation.id.toModel(),
                    DateTimeUtil.currentIsoDateTimeString(),
                    qualifiedIdMapper.fromStringToQualifiedID(event.conversation.creator),
                    Message.Status.SENT,
                    Message.Visibility.VISIBLE,
                    expirationData = null
                )
                persistMessage(message)
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event
                    )
            } else {
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SKIPPED,
                        event,
                        Pair("info", "Conversation either not group or self not a team member.")
                    )
            }
        }
        .onFailure {
            kaliumLogger
                .logEventProcessing(
                    EventLoggingStatus.FAILURE,
                    event,
                    Pair("errorInfo", "$it")
                )
        }
}
