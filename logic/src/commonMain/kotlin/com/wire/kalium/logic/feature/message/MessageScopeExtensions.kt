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

import androidx.paging.PagingConfig
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.feature.asset.GetPaginatedFlowOfAssetMessageByConversationIdUseCase
import com.wire.kalium.logic.feature.asset.ObservePaginatedAssetImageMessages

public val MessageScope.getPaginatedFlowOfMessagesByConversation: GetPaginatedFlowOfMessagesByConversationUseCase
    get() = GetPaginatedFlowOfMessagesByConversationUseCase(dispatcher, messageRepository)

public val MessageScope.getPaginatedFlowOfMessagesBySearchQueryAndConversation:
        GetPaginatedFlowOfMessagesBySearchQueryAndConversationIdUseCase
    get() = GetPaginatedFlowOfMessagesBySearchQueryAndConversationIdUseCase(dispatcher, messageRepository)

public val MessageScope.getPaginatedFlowOfAssetMessageByConversationId: GetPaginatedFlowOfAssetMessageByConversationIdUseCase
    get() = GetPaginatedFlowOfAssetMessageByConversationIdUseCase(dispatcher, messageRepository)

public val MessageScope.observePaginatedImageAssetMessageByConversationId: ObservePaginatedAssetImageMessages
    get() = ObservePaginatedAssetImageMessages(dispatcher, messageRepository)

public val MessageScope.fetchOlderMessagesByConversationId: FetchOlderNomadMessagesByConversationUseCase
    get() = FetchOlderNomadMessagesByConversationUseCaseImpl(dispatcher, messageRepository)

/**
 * Creates an iOS-friendly data source for paginated messages with explicit control.
 *
 * This provides a simpler alternative to [createPaginatedMessagesPresenter] with:
 * - Simple Flow<List<Message>> instead of ItemSnapshotList
 * - Explicit [MessageDataSource.loadMore] method for loading more messages
 * - Automatic updates when messages change in the database
 *
 * **IMPORTANT**: Call [MessageDataSource.dispose] when done to clean up resources.
 *
 * Example usage from Kotlin:
 * ```kotlin
 * val dataSource = messageScope.createMessageDataSource(
 *     conversationId = conversationId,
 *     pageSize = 50,
 *     visibility = listOf(Message.Visibility.VISIBLE)
 * )
 *
 * // Observe messages
 * dataSource.observeMessages.collect { messages ->
 *     // Update UI
 * }
 *
 * // Load more when needed
 * dataSource.loadMore()
 *
 * // Don't forget: dataSource.dispose() when done
 * ```
 *
 * @param conversationId The conversation to load messages from
 * @param pageSize Number of messages to load per page (default: 50)
 * @param visibility Which message visibilities to include
 * @return A data source that exposes messages and explicit pagination control
 */
public fun MessageScope.createMessageDataSource(
    conversationId: ConversationId,
    pageSize: Int = 50,
    visibility: List<Message.Visibility> = Message.Visibility.entries
): MessageDataSource {
    return MessageDataSource(
        conversationId = conversationId,
        messageRepository = messageRepository,
        dispatcher = dispatcher,
        pageSize = pageSize,
        visibility = visibility
    )
}
