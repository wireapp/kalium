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
package com.wire.kalium.logic.feature.conversation

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithMembers
import com.wire.kalium.logic.data.conversation.ConversationMembersQueryConfig
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Searches joined groups and channels, loading members in batches for each page.
 * When restricted, all matching conversations remain visible but only eligible members are returned.
 */
public class GetPaginatedFlowOfConversationDetailsWithMembersBySearchQueryUseCase internal constructor(
    private val dispatcher: KaliumDispatcher,
    private val conversationRepository: ConversationRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val selfUserId: UserId,
) {
    /**
     * @param queryConfig Search and conversation filters used to find joined groups and channels.
     * @param pagingConfig Page size and prefetch settings for the results.
     * @param startingOffset Offset of the first conversation in the paginated results.
     * @param strictMlsFilter Whether to exclude MLS conversations whose group state is not ready.
     * @param onlySelfTeamAndDomain When true, keep all matching conversations but return only members
     * from the current user's team and domain. If the current user has no team, member lists are empty.
     */
    public suspend operator fun invoke(
        queryConfig: ConversationMembersQueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long = 0L,
        strictMlsFilter: Boolean = true,
        onlySelfTeamAndDomain: Boolean = false,
    ): Flow<PagingData<ConversationDetailsWithMembers>> {
        val onlyTeamId = if (onlySelfTeamAndDomain) selfTeamIdProvider().getOrNull()?.value else null
        return conversationRepository.extensions.getPaginatedConversationDetailsWithMembersBySearchQuery(
            queryConfig = queryConfig,
            pagingConfig = pagingConfig,
            startingOffset = startingOffset,
            strictMlsFilter = strictMlsFilter,
        ).map { page ->
            if (onlySelfTeamAndDomain) {
                page.map { details ->
                    details.copy(
                        members = details.members.filter { member ->
                            onlyTeamId != null && member.user.teamId?.value == onlyTeamId && member.user.id.domain == selfUserId.domain
                        }
                    )
                }
            } else page
        }.flowOn(dispatcher.io)
    }
}
