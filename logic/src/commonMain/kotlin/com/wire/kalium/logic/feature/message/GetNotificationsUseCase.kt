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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import com.wire.kalium.logic.data.notification.LocalNotificationMessageMapper
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.di.MapperProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan

/**
 * Get notifications for the current user
 */
interface GetNotificationsUseCase {
    /**
     * Operation to get all notifications, the Flow emits everytime when the list is changed
     * @return [Flow] of [List] of [LocalNotificationConversation] with the List that should be shown to the user.
     */
    suspend operator fun invoke(): Flow<List<LocalNotificationConversation>>
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
    private val ephemeralNotificationsManager: EphemeralNotificationsMgr,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val localNotificationMessageMapper: LocalNotificationMessageMapper = MapperProvider.localNotificationMessageMapper()
) : GetNotificationsUseCase {

    @Suppress("LongMethod")
    override suspend operator fun invoke(): Flow<List<LocalNotificationConversation>> {
        return incrementalSyncRepository.incrementalSyncState
            .isLiveDebounced()
            .flatMapLatest { isLive ->
                if (isLive) {
                    merge(
                        messageRepository.getNotificationMessage(),
                        observeConnectionRequests(),
                        observeEphemeralNotifications()
                    )
                } else {
                    observeEphemeralNotifications()
                }
                    .map { list -> list.filter { it.messages.isNotEmpty() } }
            }
            .distinctUntilChanged()
            .filter { it.isNotEmpty() }
            .buffer(capacity = 3) // to cover a case when all 3 flows emits at the same time
    }

    private suspend fun observeEphemeralNotifications(): Flow<List<LocalNotificationConversation>> =
        ephemeralNotificationsManager.observeEphemeralNotifications().map { listOf(it) }

    private suspend fun observeConnectionRequests(): Flow<List<LocalNotificationConversation>> {
        return connectionRepository.observeConnectionRequestsForNotification()
            .map { requests ->
                requests
                    .filterIsInstance<ConversationDetails.Connection>()
                    .map { localNotificationMessageMapper.fromConnectionToLocalNotificationConversation(it) }
            }
    }

    /**
     * In case of push notification we close the connection immediately after syncing finished.
     * So event `IncrementalSyncStatus.Pending` after `IncrementalSyncStatus.Live` may come sooner
     * than notifications are handled, and cancel it.
     * This `debounce` only for the case when we were Live and non-Live event comes helps to avoid such a scenario.
     */
    private fun Flow<IncrementalSyncStatus>.isLiveDebounced(): Flow<Boolean> =
        this.map { it == IncrementalSyncStatus.Live }
            .distinctUntilChanged()
            .scan(false to false) { prevPair, isLive -> prevPair.second to isLive }
            .drop(1) // initial value of scan
            .debounce { (prevValue, newValue) ->
                if (prevValue && !newValue) AFTER_LIVE_DELAY_MS
                else 0
            }
            .map { it.second }

    companion object {
        private const val AFTER_LIVE_DELAY_MS = 100L
    }
}
