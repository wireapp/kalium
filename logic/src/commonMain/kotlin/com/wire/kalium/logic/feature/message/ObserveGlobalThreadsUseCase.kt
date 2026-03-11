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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessagePreview
import com.wire.kalium.logic.data.message.MessageThreadRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

public data class GlobalThreadSummary(
    val conversationId: ConversationId,
    val conversationName: String?,
    val conversationType: Conversation.Type,
    val otherUserPreviewAssetId: UserAssetId?,
    val otherUserAvailabilityStatus: UserAvailabilityStatus,
    val otherUserConnectionStatus: ConnectionState?,
    val otherUserId: UserId?,
    val otherUserAccentId: Int?,
    val otherUserDeleted: Boolean,
    val rootMessageId: String,
    val threadId: String,
    val visibleReplyCount: Long,
    val createdAt: Instant,
    val lastReplyDate: Instant?,
    val rootMessage: MessagePreview,
    val rootMessageSelfDeletionDurationMillis: Long?,
)

public sealed interface ObserveGlobalThreadsResult {
    public data class Success(val threads: List<GlobalThreadSummary>) : ObserveGlobalThreadsResult
    public data object Failure : ObserveGlobalThreadsResult
}

public class ObserveGlobalThreadsUseCase internal constructor(
    private val messageThreadRepository: MessageThreadRepository,
) {
    public operator fun invoke(): Flow<ObserveGlobalThreadsResult> =
        messageThreadRepository.observeGlobalThreads().map { result ->
            result.fold(
                { ObserveGlobalThreadsResult.Failure },
                { threads ->
                    ObserveGlobalThreadsResult.Success(
                        threads = threads.map {
                            GlobalThreadSummary(
                                conversationId = it.conversationId,
                                conversationName = it.conversationName,
                                conversationType = it.conversationType,
                                otherUserPreviewAssetId = it.otherUserPreviewAssetId,
                                otherUserAvailabilityStatus = it.otherUserAvailabilityStatus,
                                otherUserConnectionStatus = it.otherUserConnectionStatus,
                                otherUserId = it.otherUserId,
                                otherUserAccentId = it.otherUserAccentId,
                                otherUserDeleted = it.otherUserDeleted,
                                rootMessageId = it.rootMessageId,
                                threadId = it.threadId,
                                visibleReplyCount = it.visibleReplyCount,
                                createdAt = it.createdAt,
                                lastReplyDate = it.lastReplyDate,
                                rootMessage = it.rootMessage,
                                rootMessageSelfDeletionDurationMillis = it.rootMessageSelfDeletionDurationMillis,
                            )
                        }
                    )
                }
            )
        }
}
