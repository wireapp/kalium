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

package com.wire.kalium.logic.data.message.paging

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadAllMessagesResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadBatchRestoreRequest
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadBatchRestoreResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationWithMessages
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.nomaddevice.NomadAllMessagesMapper
import com.wire.kalium.persistence.dao.backup.NomadMessageToInsert
import com.wire.kalium.persistence.dao.backup.NomadMessagesDAO
import io.mockative.Mockable
import kotlinx.datetime.Clock

@Mockable
internal interface NomadMessagePagingCoordinator {
    /**
     * Fetches older messages for the given conversation if needed, based on the current paging state.
     * @param conversationId The ID of the conversation to fetch messages for.
     * @param pageSize The number of messages to fetch in one batch.
     * @param beforeTimestampMs Optional timestamp to fetch messages before.
     * @param onInvalidate Callback to be invoked if the paging state is updated and the UI pager consumer needs to be invalidated.
     */
    suspend fun fetchOlderMessagesIfNeeded(
        conversationId: ConversationId,
        pageSize: Int,
        beforeTimestampMs: Long?,
        onInvalidate: () -> Unit
    ): NomadMessagePagingResult
}

public data class NomadMessagePagingResult(
    val hasMore: Boolean,
)

internal class NomadMessagePagingCoordinatorImpl(
    private val selfUserId: UserId,
    private val isNomadEnabled: () -> Boolean,
    private val nomadDeviceSyncApi: NomadDeviceSyncApi,
    private val nomadMessagesDAO: NomadMessagesDAO,
    private val mapper: NomadAllMessagesMapper = NomadAllMessagesMapper(),
    private val clock: Clock = Clock.System,
) : NomadMessagePagingCoordinator {

    private data class State(
        val nextCursor: Long = 0L,
        val nextTimestamp: Long,
        val hasMore: Boolean = true,
        val isFetching: Boolean = false,
    )

    private val stateByConversation = ConcurrentMutableMap<ConversationId, State>()

    override suspend fun fetchOlderMessagesIfNeeded(
        conversationId: ConversationId,
        pageSize: Int,
        beforeTimestampMs: Long?,
        onInvalidate: () -> Unit
    ): NomadMessagePagingResult {
        kaliumLogger.d("[$TAG] Nomad paging boundary reached for conversation '${conversationId.toLogString()}'")

        val currentState = stateByConversation.block { map ->
            val existing = map[conversationId] ?: State(
                nextTimestamp = beforeTimestampMs ?: clock.now().toEpochMilliseconds()
            )
            if (!existing.hasMore || existing.isFetching) return@block null
            if (!isNomadEnabled()) {
                val next = existing.copy(hasMore = false, isFetching = false)
                map[conversationId] = next
                kaliumLogger.d("[$TAG] Nomad paging disabled for conversation '${conversationId.toLogString()}'")
                return@block null
            }

            val next = existing.copy(isFetching = true)
            map[conversationId] = next
            kaliumLogger.d(
                "[$TAG] Nomad paging fetching conversation '${conversationId.toLogString()}' " +
                        "with cursor=${next.nextCursor} ts=${next.nextTimestamp}"
            )
            next
        } ?: return NomadMessagePagingResult(hasMore = stateByConversation[conversationId]?.hasMore ?: false)

        val responseResult = wrapApiRequest {
            nomadDeviceSyncApi.restoreMessagesBatch(
                NomadBatchRestoreRequest(
                    conversationIds = listOf(conversationId.value),
                    limit = pageSize,
                    beforeTimestamp = currentState.nextTimestamp,
                    nextCursor = currentState.nextCursor,
                )
            )
        }

        return when (responseResult) {
            is Either.Left -> {
                stateByConversation.block { map ->
                    val state = map[conversationId] ?: currentState
                    val next = state.copy(isFetching = false)
                    map.put(conversationId, next)
                }
                kaliumLogger.w(
                    "[$TAG] Nomad batch restore failed for '${selfUserId.toLogString()}': ${responseResult.value}"
                )
                NomadMessagePagingResult(hasMore = stateByConversation[conversationId]?.hasMore ?: false)
            }

            is Either.Right -> storeAndUpdateState(
                conversationId = conversationId,
                response = responseResult.value,
                currentState = currentState,
                pageSize = pageSize,
                onInvalidate = onInvalidate,
            )
        }
    }

    private suspend fun storeAndUpdateState(
        conversationId: ConversationId,
        response: NomadBatchRestoreResponse,
        currentState: State,
        pageSize: Int,
        onInvalidate: () -> Unit,
    ): NomadMessagePagingResult {
        val mapped = mapper.map(response.toAllMessagesResponse())
        val storeResult = wrapStorageRequest {
            nomadMessagesDAO.storeMessages(
                messages = mapped.messages.filterByConversationId(conversationId),
                batchSize = pageSize,
            )
        }

        var shouldInvalidate = false
        var hasMore = currentState.hasMore
        stateByConversation.block { map ->
            val state = map[conversationId] ?: currentState
            when (storeResult) {
                is Either.Left -> {
                    val next = state.copy(isFetching = false)
                    map[conversationId] = next
                    hasMore = next.hasMore
                    kaliumLogger.w(
                        "[$TAG] Nomad batch restore storage failed for '${selfUserId.toLogString()}': ${storeResult.value}"
                    )
                }

                is Either.Right -> {
                    val nextState = response.nextStateFor(conversationId, state)
                    map[conversationId] = nextState
                    hasMore = nextState.hasMore
                    kaliumLogger.d(
                        "[$TAG] Nomad paging stored ${storeResult.value.storedMessages} messages for conversation " +
                                "'${conversationId.toLogString()}', hasMore=${nextState.hasMore}, " +
                                "nextCursor=${nextState.nextCursor}, nextTimestamp=${nextState.nextTimestamp}"
                    )
                    if (storeResult.value.storedMessages > 0) {
                        shouldInvalidate = true
                    }
                }
            }
        }
        if (shouldInvalidate) {
            onInvalidate()
        }
        return NomadMessagePagingResult(hasMore = hasMore)
    }

    private fun NomadBatchRestoreResponse.toAllMessagesResponse(): NomadAllMessagesResponse =
        NomadAllMessagesResponse(
            conversations = conversations.map { item ->
                NomadConversationWithMessages(
                    conversation = item.conversation,
                    messages = item.messages,
                )
            },
            hasMore = false,
            nextCursor = null,
            nextTimestamp = null,
        )

    private fun NomadBatchRestoreResponse.nextStateFor(
        conversationId: ConversationId,
        current: State,
    ): State {
        val entry = conversations.firstOrNull {
            it.conversation.id == conversationId.value && it.conversation.domain == conversationId.domain
        }
        if (entry == null) {
            return current.copy(isFetching = false, hasMore = false)
        }

        return current.copy(
            nextCursor = entry.nextCursor,
            nextTimestamp = entry.nextTimestamp,
            hasMore = entry.hasMore,
            isFetching = false,
        )
    }

    private fun List<NomadMessageToInsert>.filterByConversationId(
        conversationId: ConversationId
    ): List<NomadMessageToInsert> = filter { message ->
        message.conversationId.value == conversationId.value && message.conversationId.domain == conversationId.domain
    }

    companion object {
        const val TAG = "NomadMessagePagingCoordinator"
    }
}
