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
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Get notifications for the current user
 */
interface GetNotificationsUseCase {
    /**
     * Operation to get all notifications, the Flow emits everytime when the list is changed
     * @return [Flow] of [List] of [LocalNotification] with the List that should be shown to the user.
     */
    suspend operator fun invoke(): Flow<List<LocalNotification>>
}

/**
 *
 * @param connectionRepository connectionRepository for observing connectionRequests that user should be notified about
 * @param messageRepository MessageRepository for getting Messages that user should be notified about
 * @param localNotificationMessageMapper LocalNotificationMessageMapper for mapping PublicUser object into LocalNotificationMessageAuthor
 */
@Suppress("LongParameterList")
internal class GetNotificationsUseCaseImpl internal constructor(
    private val connectionRepository: ConnectionRepository,
    private val messageRepository: MessageRepository,
    private val deleteConversationNotificationsManager: EphemeralEventsNotificationManager,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val localNotificationMessageMapper: LocalNotificationMessageMapper = MapperProvider.localNotificationMessageMapper()
) : GetNotificationsUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("LongMethod")
    override suspend operator fun invoke(): Flow<List<LocalNotification>> {
        return incrementalSyncRepository.incrementalSyncState
            .map { it != IncrementalSyncStatus.FetchingPendingEvents }
            .flatMapLatest { isLive ->
                if (isLive) {
                    merge(
                        messageRepository.getNotificationMessage().fold({ flowOf() }, { it }),
                        observeConnectionRequests(),
                        observeEphemeralNotifications()
                    )
                } else {
                    observeEphemeralNotifications()
                }
                    .map { list ->
                        list.filter { it !is LocalNotification.Conversation || it.messages.isNotEmpty() }
                    }
            }
            .filter { it.isNotEmpty() }
    }

    private suspend fun observeEphemeralNotifications(): Flow<List<LocalNotification>> =
        deleteConversationNotificationsManager.observeEphemeralNotifications().map { listOf(it) }

    private suspend fun observeConnectionRequests(): Flow<List<LocalNotification>> {
        return connectionRepository.observeConnectionRequestsForNotification()
            .map { requests ->
                requests
                    .filterIsInstance<ConversationDetails.Connection>()
                    .map { localNotificationMessageMapper.fromConnectionToLocalNotificationConversation(it) }
            }
    }
}
