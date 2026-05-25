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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.LocalNotificationMessageMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.common.functional.onlyRight
import kotlinx.coroutines.flow.first

/**
 * Get the current snapshot of notifications for the user.
 * Unlike [GetNotificationsUseCase], this use case fetches notifications once and completes,
 * rather than observing for continuous updates.
 */
public interface GetCurrentNotificationsUseCase {
    /**
     * Fetches the current list of notifications without observing for changes.
     * This includes regular message notifications and connection requests.
     * Ephemeral notifications are excluded as they are meant for real-time observation.
     *
     * @return [List] of [LocalNotification] representing the current state.
     */
    public suspend operator fun invoke(): List<LocalNotification>
}

/**
 * Implementation of [GetCurrentNotificationsUseCase].
 *
 * @param connectionRepository ConnectionRepository for getting connection requests
 * @param messageRepository MessageRepository for getting messages that should be notified
 * @param localNotificationMessageMapper Mapper for converting connection data to notification format
 */
internal class GetCurrentNotificationsUseCaseImpl internal constructor(
    private val connectionRepository: ConnectionRepository,
    private val messageRepository: MessageRepository,
    private val localNotificationMessageMapper: LocalNotificationMessageMapper = MapperProvider.localNotificationMessageMapper()
) : GetCurrentNotificationsUseCase {

    override suspend operator fun invoke(): List<LocalNotification> {
        // Get regular message notifications
        val messageNotifications = when (val result = messageRepository.getNotificationMessage()) {
            is com.wire.kalium.common.functional.Either.Right -> result.value
            is com.wire.kalium.common.functional.Either.Left -> emptyList()
        }

        // Get current connection requests
        val connectionNotifications = connectionRepository.observeConnectionRequestsForNotification()
            .first()
            .filterIsInstance<ConversationDetails.Connection>()
            .map { localNotificationMessageMapper.fromConnectionToLocalNotificationConversation(it) }

        // Combine and filter
        return (messageNotifications + connectionNotifications)
            .filter { it !is LocalNotification.Conversation || it.messages.isNotEmpty() }
    }
}
