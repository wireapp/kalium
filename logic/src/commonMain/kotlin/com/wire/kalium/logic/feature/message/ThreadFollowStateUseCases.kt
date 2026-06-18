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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageThreadRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.sending.MessageSender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

public sealed interface ObserveThreadFollowStateResult {
    public data class Success(val isFollowing: Boolean) : ObserveThreadFollowStateResult
    public data object Failure : ObserveThreadFollowStateResult
}

public class ObserveThreadFollowStateUseCase internal constructor(
    private val messageThreadRepository: MessageThreadRepository,
) {
    public operator fun invoke(
        conversationId: ConversationId,
        threadId: String,
    ): Flow<ObserveThreadFollowStateResult> =
        messageThreadRepository.observeThreadFollowState(conversationId, threadId).map { result ->
            result.fold(
                { ObserveThreadFollowStateResult.Failure },
                { isFollowing -> ObserveThreadFollowStateResult.Success(isFollowing ?: true) }
            )
        }
}

public sealed interface SetThreadFollowStateResult {
    public data object Success : SetThreadFollowStateResult
    public data class Failure(val failure: CoreFailure) : SetThreadFollowStateResult
}

public class SetThreadFollowStateUseCase internal constructor(
    private val messageThreadRepository: MessageThreadRepository,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfUserId: UserId,
    private val messageSender: MessageSender,
) {
    public suspend operator fun invoke(
        conversationId: ConversationId,
        threadId: String,
        isFollowing: Boolean,
    ): SetThreadFollowStateResult =
        messageThreadRepository.updateThreadFollowState(conversationId, threadId, isFollowing)
            .flatMap {
                sendThreadFollowStateToOtherClients(conversationId, threadId, isFollowing)
            }
            .fold(
                { SetThreadFollowStateResult.Failure(it) },
                { SetThreadFollowStateResult.Success }
            )

    private suspend fun sendThreadFollowStateToOtherClients(
        conversationId: ConversationId,
        threadId: String,
        isFollowing: Boolean,
    ): Either<CoreFailure, Unit> =
        currentClientIdProvider().flatMap { currentClientId ->
            selfConversationIdProvider().flatMap { selfConversationIds ->
                selfConversationIds.foldToEitherWhileRight(Unit) { selfConversationId, _ ->
                    val messageId = Uuid.random().toString()
                    messageSender.sendMessage(
                        Message.Signaling(
                            id = messageId,
                            content = MessageContent.ThreadFollow(
                                conversationId = conversationId,
                                threadId = threadId,
                                isFollowing = isFollowing,
                            ),
                            conversationId = selfConversationId,
                            date = Clock.System.now(),
                            senderUserId = selfUserId,
                            senderClientId = currentClientId,
                            status = Message.Status.Pending,
                            isSelfMessage = true,
                            expirationData = null,
                        )
                    )
                }
            }
        }
}
