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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * iOS-friendly data source for paginated message loading with explicit pagination control.
 *
 * This class provides a simplified API for loading messages with manual pagination,
 * hiding all AndroidX Paging complexity. It exposes:
 * - [observeMessages]: A Flow of currently loaded messages
 * - [loadMore]: Explicit method to load the next page
 *
 * **IMPORTANT**: Call [dispose] when done to clean up resources.
 *
 * Usage from iOS (Swift):
 * ```swift
 * let dataSource = messageScope.createMessageDataSource(
 *     conversationId: conversationId
 * )
 *
 * // Observe messages
 * for await messages in dataSource.observeMessages() {
 *     // Update UI with messages
 * }
 *
 * // Load more when needed (e.g., user scrolls to bottom)
 * dataSource.loadMore()
 *
 * // When done
 * dataSource.dispose()
 * ```
 *
 * @param conversationId The conversation to load messages from
 * @param messageRepository The repository for fetching messages
 * @param dispatcher Dispatcher for IO operations
 * @param pageSize Number of messages to load per page (default: 50)
 * @param visibility Which message visibilities to include
 */
public class MessageDataSource internal constructor(
    private val conversationId: ConversationId,
    private val messageRepository: MessageRepository,
    private val dispatcher: KaliumDispatcher,
    private val pageSize: Int = 50,
    private val visibility: List<Message.Visibility> = Message.Visibility.entries
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _currentLimit = MutableStateFlow(pageSize)
    private val _messages = MutableStateFlow<List<Message.Standalone>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _hasMore = MutableStateFlow(true)

    /**
     * Flow of currently loaded messages.
     * Emits a new list whenever:
     * - More messages are loaded via [loadMore]
     * - Messages in the database change (new messages, edits, deletions)
     */
    public val observeMessages: Flow<List<Message.Standalone>> = _messages.asStateFlow()

    /**
     * Whether messages are currently being loaded.
     */
    public val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Whether more messages are available to load.
     */
    public val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    init {
        // Start observing messages with initial limit
        scope.launch(dispatcher.io) {
            _currentLimit.collectLatest { limit ->
                    _isLoading.value = true
                    messageRepository.getMessagesByConversationIdAndVisibility(
                        conversationId = conversationId,
                        limit = limit,
                        offset = 0,
                        visibility = visibility
                    )
                        .map { messages ->
                            messages.filterIsInstance<Message.Standalone>()
                        }
                        .collectLatest { messages ->
                            _messages.value = messages
                            _hasMore.value = messages.size >= limit
                            _isLoading.value = false
                        }
                }
        }
    }

    /**
     * Load the next page of messages.
     *
     * This increases the query limit to fetch more messages from the database.
     * The new messages will be emitted through [observeMessages].
     *
     * Safe to call multiple times - subsequent calls while loading will be ignored.
     */
    public fun loadMore() {
        if (_isLoading.value || !_hasMore.value) {
            return
        }
        _currentLimit.value += pageSize
    }

    /**
     * Reload messages from the beginning.
     *
     * Resets pagination state and fetches the first page again.
     * Useful for pull-to-refresh scenarios.
     */
    public fun reload() {
        _currentLimit.value = pageSize
        _hasMore.value = true
    }

    /**
     * Dispose of the data source and clean up resources.
     *
     * **IMPORTANT**: This must be called when you're done with the data source
     * to prevent memory leaks. From iOS, call this in deinit or when the
     * view disappears.
     */
    public fun dispose() {
        scope.cancel()
    }
}
