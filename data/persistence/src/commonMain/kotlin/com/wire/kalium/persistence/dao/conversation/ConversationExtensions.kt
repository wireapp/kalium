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
package com.wire.kalium.persistence.dao.conversation

import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import app.cash.sqldelight.async.coroutines.awaitAsList
import com.wire.kalium.persistence.ConversationDetailsWithEventsQueries
import com.wire.kalium.persistence.ConversationDetailsWithMembersQueries
import com.wire.kalium.persistence.MembersQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationExtensions.QueryConfig
import com.wire.kalium.persistence.dao.message.AsyncQueryPagingSource
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.db.ReadDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlin.collections.map
import kotlin.collections.orEmpty

interface ConversationExtensions {
    fun getPagerForConversationDetailsWithEventsSearch(
        queryConfig: QueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long = 0,
    ): KaliumPager<ConversationDetailsWithEventsEntity>

    data class QueryConfig(
        val searchQuery: String = "",
        val fromArchive: Boolean = false,
        val onlyInteractionEnabled: Boolean = false,
        val newActivitiesOnTop: Boolean = false,
        val ongoingCallConversationIds: List<QualifiedIDEntity> = emptyList(),
        val ongoingCallConversationIdsFlow: Flow<List<QualifiedIDEntity>> = flowOf(ongoingCallConversationIds),
        val conversationFilter: ConversationFilterEntity = ConversationFilterEntity.ALL,
        val strictMlsFilter: Boolean = true,
    )

    fun getPagerForConversationDetailsWithMembersSearch(
        queryConfig: MembersQueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long = 0,
    ): KaliumPager<ConversationDetailsWithMembersEntity>

    data class MembersQueryConfig(
        val searchQuery: String = "",
        val fromArchive: Boolean = false,
        val onlyInteractionEnabled: Boolean = false,
        val onlyTeamId: String? = null,
        val onlyDomain: String? = null,
        val strictMlsFilter: Boolean = true,
    )
}

internal class ConversationExtensionsImpl internal constructor(
    private val conversationDetailsWithEventsQueries: ConversationDetailsWithEventsQueries,
    private val conversationDetailsWithMembersQueries: ConversationDetailsWithMembersQueries,
    private val memberQueries: MembersQueries,
    private val mapper: ConversationDetailsWithEventsMapper,
    private val readDispatcher: ReadDispatcher,
) : ConversationExtensions {
    override fun getPagerForConversationDetailsWithEventsSearch(
        queryConfig: QueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<ConversationDetailsWithEventsEntity> {
        val ongoingCallConversationIds = MutableStateFlow(queryConfig.ongoingCallConversationIds)
        val pagingSourceFactory = InvalidatingPagingSourceFactory {
            pagingSource(
                queryConfig = queryConfig,
                initialOffset = startingOffset,
                ongoingCallConversationIds = { ongoingCallConversationIds.value }
            )
        }
        return KaliumPager(
            // We could return a Flow directly, but having the PagingSource is the only way to test this
            pager = Pager(pagingConfig) {
                pagingSourceFactory()
            },
            pagingSource = pagingSource(
                queryConfig = queryConfig,
                initialOffset = startingOffset,
                ongoingCallConversationIds = { ongoingCallConversationIds.value }
            ),
            readDispatcher = readDispatcher,
            invalidateOn = queryConfig.ongoingCallConversationIdsFlow
                .distinctUntilChanged()
                .onEach { ongoingCallConversationIds.value = it }
                .dropWhile { it == queryConfig.ongoingCallConversationIds },
            invalidatePagingSource = pagingSourceFactory::invalidate
        )
    }

    /**
     * Uses lightweight COUNT when `searchQuery` is empty.
     *
     * Architectural decision explained in ADR-003:
     * https://github.com/wireapp/kalium/tree/develop/docs/adr/0003-lightweight-count-for-faster-conversation-list-loading.md
     *
     * Summary:
     * - COUNT on Conversation is significantly faster than COUNT on ConversationDetails
     * - Only used when search is empty
     * - SELECT still uses full ConversationDetails rules (COUNT can be a superset)
     */
    private fun pagingSource(
        queryConfig: QueryConfig,
        initialOffset: Long,
        ongoingCallConversationIds: () -> List<QualifiedIDEntity>
    ) = with(queryConfig) {
        AsyncQueryPagingSource(
            countQuery =
                if (searchQuery.isBlank()) {
                    conversationDetailsWithEventsQueries.countConversations(
                        fromArchive = fromArchive,
                        conversationFilter = conversationFilter.name,
                        strict_mls = if (strictMlsFilter) 1 else 0,
                    )
                } else {
                    conversationDetailsWithEventsQueries.countConversationDetailsWithEventsFromSearch(
                        fromArchive = fromArchive,
                        onlyInteractionsEnabled = onlyInteractionEnabled,
                        conversationFilter = conversationFilter.name,
                        searchQuery = searchQuery,
                        strict_mls = if (strictMlsFilter) 1 else 0,
                    )
                },
            context = readDispatcher.value,
            initialOffset = initialOffset,
            queryProvider = { limit, offset ->
                if (searchQuery.isBlank()) {
                    conversationDetailsWithEventsQueries.selectConversationDetailsWithEvents(
                        fromArchive = fromArchive,
                        onlyInteractionsEnabled = onlyInteractionEnabled,
                        conversationFilter = conversationFilter.name,
                        newActivitiesOnTop = newActivitiesOnTop,
                        ongoingCallConversationIds = ongoingCallConversationIds(),
                        limit = limit,
                        offset = offset,
                        strict_mls = if (queryConfig.strictMlsFilter) 1 else 0,
                        mapper = mapper::fromViewToModel,
                    )
                } else {
                    conversationDetailsWithEventsQueries.selectConversationDetailsWithEventsFromSearch(
                        fromArchive = fromArchive,
                        onlyInteractionsEnabled = onlyInteractionEnabled,
                        conversationFilter = conversationFilter.name,
                        searchQuery = searchQuery,
                        newActivitiesOnTop = newActivitiesOnTop,
                        ongoingCallConversationIds = ongoingCallConversationIds(),
                        limit = limit,
                        offset = offset,
                        strict_mls = if (queryConfig.strictMlsFilter) 1 else 0,
                        mapper = mapper::fromViewToModel,
                    )
                }
            }
        )
    }

    override fun getPagerForConversationDetailsWithMembersSearch(
        queryConfig: ConversationExtensions.MembersQueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long,
    ): KaliumPager<ConversationDetailsWithMembersEntity> = KaliumPager(
        pager = Pager(pagingConfig) { membersPagingSource(queryConfig, startingOffset) },
        pagingSource = membersPagingSource(queryConfig, startingOffset),
        readDispatcher = readDispatcher,
    )

    private fun membersPagingSource(config: ConversationExtensions.MembersQueryConfig, initialOffset: Long) =
        AsyncQueryPagingSource(
            countQuery = conversationDetailsWithMembersQueries.countConversationDetailsForMemberSearch(
                fromArchive = config.fromArchive,
                onlyInteractionsEnabled = if (config.onlyInteractionEnabled) 1L else 0L,
                strict_mls = if (config.strictMlsFilter) 1 else 0,
                searchQuery = config.searchQuery,
                onlyTeamId = config.onlyTeamId,
                onlyDomain = config.onlyDomain,
            ),
            context = readDispatcher.value,
            initialOffset = initialOffset,
            queryProvider = { limit, offset ->
                conversationDetailsWithMembersQueries.selectConversationDetailsForMemberSearch(
                    fromArchive = config.fromArchive,
                    onlyInteractionsEnabled = if (config.onlyInteractionEnabled) 1L else 0L,
                    strict_mls = if (config.strictMlsFilter) 1 else 0,
                    searchQuery = config.searchQuery,
                    onlyTeamId = config.onlyTeamId,
                    onlyDomain = config.onlyDomain,
                    limit = limit,
                    offset = offset,
                    mapper = ConversationMapper::fromViewToModel,
                )
            },
            postProcessor = { conversations ->
                val members = if (conversations.isEmpty()) emptyMap() else memberQueries
                    .selectMembersForConversations(conversations.map { it.id }).awaitAsList()
                    .groupBy { it.conversation }
                conversations.map { conversation ->
                    ConversationDetailsWithMembersEntity(
                        conversationViewEntity = conversation,
                        members = members[conversation.id].orEmpty().map { it.toMemberWithUserEntity() },
                    )
                }
            },
            transacter = conversationDetailsWithMembersQueries,
        )
}
