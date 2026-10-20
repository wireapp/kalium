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

import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.notification.EphemeralConversationNotification
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.notification.toLocalNotificationMessageAuthor
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.UserSessionBinding
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.util.EventLoggingStatus
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.firstOrNull

internal interface DeletedConversationEventHandler {
    suspend fun handle(transactionContext: CryptoTransactionContext, event: Event.Conversation.DeletedConversation)
}

@Inject
@Suppress("LongParameterList")
internal class DeletedConversationEventHandlerImpl(
    @UserSessionBinding("userRepository")
    private val userRepository: UserRepository,
    @UserSessionBinding("conversationRepository")
    private val conversationRepository: ConversationRepository,
    @UserSessionBinding("meetingRepository")
    private val meetingRepository: MeetingRepository,
    @UserSessionBinding("notificationEventsManager")
    private val notificationEventsManager: NotificationEventsManager,
    @UserSessionBinding("deleteConversationUseCase")
    private val deleteConversation: DeleteConversationUseCase,
    @UserSessionBinding("currentPersistenceEventHookNotifier")
    private val persistenceEventHookNotifier: PersistenceEventHookNotifier,
    @UserSessionBinding("userId")
    private val selfUserId: UserId,
) : DeletedConversationEventHandler {

    override suspend fun handle(transactionContext: CryptoTransactionContext, event: Event.Conversation.DeletedConversation) {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        conversationRepository.getConversationById(event.conversationId)
            .onFailure {
                logger.logComplete(
                    EventLoggingStatus.SKIPPED,
                    arrayOf(
                        "info" to "Conversation delete event already handled?. Couldn't find the conversation."
                    )
                )
            }
            .map { conversation ->
                conversation to if (conversation.type == Conversation.Type.Group.Meeting) {
                    meetingRepository.getMeetingsByConversationId(conversation.id).getOrElse { emptyList() }
                } else emptyList()
            }
            .flatMap { (conversation, meetings) ->
                deleteConversation(transactionContext, event.conversationId)
                    .onFailure {
                        logger.logFailure(it)
                    }.onSuccess {
                        val senderUser = event.senderUserId?.let { userRepository.observeUser(it).firstOrNull() }
                        val dataNotification = EphemeralConversationNotification(event, conversation, senderUser)
                        if (conversation.type != Conversation.Type.Group.Meeting) {
                            notificationEventsManager.scheduleDeleteConversationNotification(dataNotification)
                        } else {
                            meetings.forEach { meeting ->
                                notificationEventsManager.scheduleMeetingNotification(
                                    LocalNotification.Meeting.Cancel(
                                        eventId = event.id,
                                        meetingId = meeting.meetingId,
                                        conversationId = meeting.conversationId,
                                        meetingTitle = meeting.title,
                                        author = senderUser?.toLocalNotificationMessageAuthor(),
                                        time = event.dateTime,
                                    )
                                )
                            }
                        }
                        logger.logSuccess()
                    }
            }
        persistenceEventHookNotifier.onConversationDeleted(
            ConversationDeleteEventData(event.conversationId),
            selfUserId
        )
    }
}
