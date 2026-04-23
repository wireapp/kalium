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
package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.ConversationId
import io.mockative.Mockable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

@Mockable
internal interface CallModerationActionsRepository {
    suspend fun addAction(conversationId: ConversationId, callModerationAction: CallModerationAction)
    fun observeActions(conversationId: ConversationId): Flow<CallModerationAction>
}

internal class CallModerationActionsDataSource : CallModerationActionsRepository {
    private val _actionChannels = MutableStateFlow<Map<ActionKey, Channel<CallModerationAction>>>(emptyMap())

    override suspend fun addAction(conversationId: ConversationId, callModerationAction: CallModerationAction) {
        val key = conversationId to callModerationAction.type
        _actionChannels.updateAndGet { currentMap ->
            when {
                currentMap.containsKey(key) -> currentMap // if a channel for this conversation and action type already exists, keep it
                else -> currentMap + (key to Channel(Channel.CONFLATED)) // otherwise, create a new one and add it to the map
            }
        }[key]?.send(callModerationAction) // send the action to the corresponding channel
    }

    /**
     * This implementation allows us to have a separate channel for each combination of conversation and action type.
     * When observing actions for a conversation, we merge the flows from all relevant channels, so that we get a single stream of actions
     * regardless of their type. This also means that if a new action type is added for the same conversation while observing,
     * it will be automatically included in the merged flow without needing to restart the observation.
     * Actions are emitted in the order they were added, and if an action is added before observing, it will be emitted
     * as soon as the observation starts, because CONFLATED buffer keeps the most recent value and emits it to new subscribers.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActions(conversationId: ConversationId): Flow<CallModerationAction> =
        _actionChannels
            .map { allChannels ->
                // filter only channels belonging to this conversationId, regardless of action type
                allChannels.filterKeys { it.conversationId == conversationId }.values
            }
            // react only when the number of channels changes (a new action type is added for this conversation)
            .distinctUntilChanged { old, new -> old.size == new.size }
            .flatMapLatest { conversationChannels ->
                // merge all channels into a single stream that "consumes" the data
                conversationChannels.map { it.receiveAsFlow() }.merge()
            }

    fun clearActions(conversationId: ConversationId) {
        val channelsToClose = mutableListOf<Channel<CallModerationAction>>()
        _actionChannels.update { currentMap ->
            // distinguish channels to remove (belonging to the ended conversation) and those to keep (other conversations)
            val (toRemove, toKeep) = currentMap.entries.partition { it.key.conversationId == conversationId }
            // collect channels to close after the update
            channelsToClose.addAll(toRemove.map { it.value })
            // return the map with the ended conversation's channels removed
            toKeep.associate { it.key to it.value }
        }
        // close channels outside the state update to avoid potential issues with concurrent access
        channelsToClose.forEach(Channel<CallModerationAction>::close)
    }
}

private data class ActionKey(val conversationId: ConversationId, val actionType: CallModerationAction.Type)
private infix fun ConversationId.to(actionType: CallModerationAction.Type) = ActionKey(this, actionType)
