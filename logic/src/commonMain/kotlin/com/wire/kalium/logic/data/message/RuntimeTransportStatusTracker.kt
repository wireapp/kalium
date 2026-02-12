/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.message

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.isInProgress
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Public read-only view of the runtime transport status.
 * Observe [updates] in the UI to react to changes, then query
 * [isMessageSending] / [getAssetInProgressStatus] for individual items.
 */
public interface RuntimeTransportStatusObserver {
    public val updates: Flow<Unit>
    public fun isMessageSending(conversationId: ConversationId, messageId: String): Boolean
    public fun getAssetInProgressStatus(conversationId: ConversationId, messageId: String): AssetTransferStatus?
}

internal interface RuntimeTransportStatusTracker : RuntimeTransportStatusObserver {

    fun markMessageSending(conversationId: ConversationId, messageId: String)
    fun clearMessageSending(conversationId: ConversationId, messageId: String)

    fun markAssetInProgress(conversationId: ConversationId, messageId: String, transferStatus: AssetTransferStatus)
    fun clearAssetInProgress(conversationId: ConversationId, messageId: String)
}

internal object NoOpRuntimeTransportStatusTracker : RuntimeTransportStatusTracker {
    private val noUpdates = MutableStateFlow(Unit)
    override val updates: Flow<Unit> = noUpdates

    override fun isMessageSending(conversationId: ConversationId, messageId: String): Boolean = false
    override fun getAssetInProgressStatus(conversationId: ConversationId, messageId: String): AssetTransferStatus? = null
    override fun markMessageSending(conversationId: ConversationId, messageId: String) = Unit
    override fun clearMessageSending(conversationId: ConversationId, messageId: String) = Unit
    override fun markAssetInProgress(conversationId: ConversationId, messageId: String, transferStatus: AssetTransferStatus) = Unit
    override fun clearAssetInProgress(conversationId: ConversationId, messageId: String) = Unit
}

internal class RuntimeTransportStatusTrackerImpl(
    private val sendingMessages: ConcurrentMutableMap<TransportStatusKey, Unit> = ConcurrentMutableMap(),
    private val inProgressAssets: ConcurrentMutableMap<TransportStatusKey, AssetTransferStatus> = ConcurrentMutableMap(),
) : RuntimeTransportStatusTracker {

    private val updateCounter = MutableStateFlow(0L)
    override val updates: Flow<Unit> = updateCounter.map { Unit }

    override fun markMessageSending(conversationId: ConversationId, messageId: String) {
        val key = TransportStatusKey(conversationId, messageId)
        val didChange = sendingMessages.block { map ->
            if (map.containsKey(key)) {
                false
            } else {
                map[key] = Unit
                true
            }
        }
        if (didChange) notifyUpdates()
    }

    override fun clearMessageSending(conversationId: ConversationId, messageId: String) {
        val didChange = sendingMessages.block { it.remove(TransportStatusKey(conversationId, messageId)) != null }
        if (didChange) notifyUpdates()
    }

    override fun isMessageSending(conversationId: ConversationId, messageId: String): Boolean =
        sendingMessages.containsKey(TransportStatusKey(conversationId, messageId))

    override fun markAssetInProgress(conversationId: ConversationId, messageId: String, transferStatus: AssetTransferStatus) {
        if (!transferStatus.isInProgress()) {
            clearAssetInProgress(conversationId, messageId)
            return
        }

        val key = TransportStatusKey(conversationId, messageId)
        val didChange = inProgressAssets.block { map ->
            val previous = map[key]
            if (previous == transferStatus) {
                false
            } else {
                map[key] = transferStatus
                true
            }
        }
        if (didChange) notifyUpdates()
    }

    override fun clearAssetInProgress(conversationId: ConversationId, messageId: String) {
        val didChange = inProgressAssets.block { it.remove(TransportStatusKey(conversationId, messageId)) != null }
        if (didChange) notifyUpdates()
    }

    override fun getAssetInProgressStatus(conversationId: ConversationId, messageId: String): AssetTransferStatus? =
        inProgressAssets[TransportStatusKey(conversationId, messageId)]

    private fun notifyUpdates() {
        updateCounter.update { it + 1 }
    }
}

internal data class TransportStatusKey(
    val conversationId: ConversationId,
    val messageId: String
)
