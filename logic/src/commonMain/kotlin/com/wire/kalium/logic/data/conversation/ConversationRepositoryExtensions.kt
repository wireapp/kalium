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
package com.wire.kalium.logic.data.conversation

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.ConversationExtensions.MembersQueryConfig
import com.wire.kalium.persistence.dao.conversation.ConversationExtensions.QueryConfig
import com.wire.kalium.persistence.dao.conversation.ConversationFilterEntity
import com.wire.kalium.persistence.dao.conversation.ConversationListPositionEntity
import com.wire.kalium.persistence.dao.conversation.ConversationListQueryEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.dao.message.KaliumPager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal interface ConversationRepositoryExtensions {
    fun observeConversationListItems(
        pagingConfig: PagingConfig,
        query: ConversationListQuery,
        initialPosition: ConversationListPosition?,
    ): Flow<PagingData<ConversationListItem>>

    suspend fun getPaginatedConversationDetailsWithEventsBySearchQuery(
        queryConfig: ConversationQueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long,
        strictMlsFilter: Boolean,
        ongoingCallConversationIds: List<ConversationId> = emptyList(),
        ongoingCallConversationIdsFlow: Flow<List<ConversationId>> = flowOf(ongoingCallConversationIds),
    ): Flow<PagingData<ConversationDetailsWithEvents>>

    suspend fun getPaginatedConversationDetailsWithMembersBySearchQuery(
        queryConfig: ConversationMembersQueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long,
        strictMlsFilter: Boolean,
    ): Flow<PagingData<ConversationDetailsWithMembers>>

}

internal class ConversationRepositoryExtensionsImpl internal constructor(
    private val selfUserId: UserId,
    private val conversationDAO: ConversationDAO,
    private val conversationMapper: ConversationMapper,
    private val userMapper: UserMapper,
    private val memberMapper: MemberMapper,
) : ConversationRepositoryExtensions {
    override fun observeConversationListItems(
        pagingConfig: PagingConfig,
        query: ConversationListQuery,
        initialPosition: ConversationListPosition?,
    ): Flow<PagingData<ConversationListItem>> = conversationDAO.observeConversationListItems(
        pagingConfig = pagingConfig,
        query = ConversationListQueryEntity(
            source = when (query.source) {
                ConversationListSource.ALL -> ConversationFilterEntity.ALL
                ConversationListSource.GROUPS -> ConversationFilterEntity.GROUPS
                ConversationListSource.CHANNELS -> ConversationFilterEntity.CHANNELS
                ConversationListSource.ONE_ON_ONE -> ConversationFilterEntity.ONE_ON_ONE
            },
            searchQuery = query.searchQuery,
            fromArchive = query.fromArchive,
            onlyInteractionEnabled = query.onlyInteractionEnabled,
            strictMlsFilter = query.strictMlsFilter,
            unreadOnly = query.unreadOnly,
        ),
        initialPosition = initialPosition?.let { ConversationListPositionEntity(it.activityTime, it.conversationId.toDao()) },
    ).map { page -> page.map(conversationMapper::fromListItemEntity) }

    override suspend fun getPaginatedConversationDetailsWithEventsBySearchQuery(
        queryConfig: ConversationQueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long,
        strictMlsFilter: Boolean,
        ongoingCallConversationIds: List<ConversationId>,
        ongoingCallConversationIdsFlow: Flow<List<ConversationId>>,
    ): Flow<PagingData<ConversationDetailsWithEvents>> {
        val pager: KaliumPager<ConversationDetailsWithEventsEntity> = with(queryConfig) {
            conversationDAO.platformExtensions.getPagerForConversationDetailsWithEventsSearch(
                queryConfig = QueryConfig(
                    searchQuery = searchQuery,
                    fromArchive = fromArchive,
                    onlyInteractionEnabled = onlyInteractionEnabled,
                    newActivitiesOnTop = newActivitiesOnTop,
                    ongoingCallConversationIds = ongoingCallConversationIds.map { it.toDao() },
                    ongoingCallConversationIdsFlow = ongoingCallConversationIdsFlow.map { ids -> ids.map { it.toDao() } },
                    conversationFilter = conversationFilter.toDao(),
                    strictMlsFilter = strictMlsFilter
                ),
                pagingConfig = pagingConfig
            )
        }

        return pager.pagingDataFlow.map { pagingData ->
            pagingData
                .map { conversationDetailsWithEventsEntity ->
                    conversationMapper.fromDaoModelToDetailsWithEvents(
                        conversationDetailsWithEventsEntity
                    )
                }
        }
    }

    override suspend fun getPaginatedConversationDetailsWithMembersBySearchQuery(
        queryConfig: ConversationMembersQueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long,
        strictMlsFilter: Boolean,
    ): Flow<PagingData<ConversationDetailsWithMembers>> {
        val pager = conversationDAO.platformExtensions.getPagerForConversationDetailsWithMembersSearch(
            queryConfig = MembersQueryConfig(
                searchQuery = queryConfig.searchQuery,
                fromArchive = queryConfig.fromArchive,
                onlyInteractionEnabled = queryConfig.onlyInteractionEnabled,
                onlyTeamId = queryConfig.onlyTeamId,
                onlyDomain = queryConfig.onlyDomain,
                strictMlsFilter = strictMlsFilter,
            ),
            pagingConfig = pagingConfig,
            startingOffset = startingOffset,
        )
        return pager.pagingDataFlow.map { page ->
            page.map { entity ->
                ConversationDetailsWithMembers(
                    conversationDetails = conversationMapper.fromDaoModelToDetails(entity.conversationViewEntity),
                    members = entity.members.map { member ->
                        MemberDetails(
                            user = when (member.user.id) {
                                selfUserId.toDao() -> userMapper.fromUserEntityToSelfUser(member.user)
                                else -> userMapper.fromUserEntityToOtherUser(member.user)
                            },
                            role = memberMapper.fromDaoModel(MemberEntity(member.user.id, member.role)).role,
                        )
                    },
                )
            }
        }
    }

}

public data class ConversationQueryConfig(
    val searchQuery: String = "",
    val fromArchive: Boolean = false,
    val onlyInteractionEnabled: Boolean = false,
    val newActivitiesOnTop: Boolean = false,
    val conversationFilter: ConversationFilter = ConversationFilter.All,
)

public data class ConversationMembersQueryConfig(
    val searchQuery: String = "",
    val fromArchive: Boolean = false,
    val onlyInteractionEnabled: Boolean = false,
    val onlyTeamId: String? = null,
    val onlyDomain: String? = null,
)
