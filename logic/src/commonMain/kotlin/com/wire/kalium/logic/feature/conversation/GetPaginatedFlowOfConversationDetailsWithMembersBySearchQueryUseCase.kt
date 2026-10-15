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
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithMembers
import com.wire.kalium.logic.data.conversation.ConversationMembersQueryConfig
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf

/** Searches joined groups and channels, loading members in batches for each page. */
public class GetPaginatedFlowOfConversationDetailsWithMembersBySearchQueryUseCase internal constructor(
    private val dispatcher: KaliumDispatcher,
    private val conversationRepository: ConversationRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val selfUserId: UserId,
) {
    public suspend operator fun invoke(
        queryConfig: ConversationMembersQueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long = 0L,
        strictMlsFilter: Boolean = true,
        onlySelfTeamAndDomain: Boolean = false,
    ): Flow<PagingData<ConversationDetailsWithMembers>> {
        val onlyTeamId = if (onlySelfTeamAndDomain) {
            // A missing team must not turn restricted search into unrestricted search.
            selfTeamIdProvider().getOrNull()?.value ?: return flowOf(PagingData.empty())
        } else null
        return conversationRepository.extensions.getPaginatedConversationDetailsWithMembersBySearchQuery(
            queryConfig = if (onlySelfTeamAndDomain) queryConfig.copy(
                onlyTeamId = onlyTeamId,
                onlyDomain = selfUserId.domain,
            ) else queryConfig,
            pagingConfig = pagingConfig,
            startingOffset = startingOffset,
            strictMlsFilter = strictMlsFilter,
        ).flowOn(dispatcher.io)
    }
}
