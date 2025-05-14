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

package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.user.ConnectionState
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Deprecated(
    "Name is misleading and will be removed in the future",
    ReplaceWith("ObservePendingConnectionRequestsUseCase")
)
typealias ObserveConnectionListUseCase = ObservePendingConnectionRequestsUseCase

/**
 * Use Case that lists the current pending connection requests.
 *
 * Only connections that are [ConnectionState.PENDING] or [ConnectionState.SENT] are returned.
 *
 * @see ConnectionState
 */
@Mockable
fun interface ObservePendingConnectionRequestsUseCase {
    /**
     * Use case [ObservePendingConnectionRequestsUseCase] operation
     *
     * @return a [Flow] with a list of [ConversationDetails] containing all current connections
     */
    suspend operator fun invoke(): Flow<List<ConversationDetails.Connection>>
}

internal class ObservePendingConnectionRequestsUseCaseImpl internal constructor(
    private val connectionRepository: ConnectionRepository,
) : ObservePendingConnectionRequestsUseCase {

    override suspend operator fun invoke(): Flow<List<ConversationDetails.Connection>> {
        return connectionRepository.observeConnectionRequestList()
            .map { conversationDetails ->
                /** Ignored connections are filtered because they should not be visible
                 *  in conversation list
                 */
                conversationDetails.filter {
                    it.connection.status != ConnectionState.IGNORED
                }
            }.distinctUntilChanged()
    }
}
