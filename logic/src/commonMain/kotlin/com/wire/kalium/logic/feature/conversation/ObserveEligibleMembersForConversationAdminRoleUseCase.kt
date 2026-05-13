/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.isAppOrBot
import com.wire.kalium.logic.data.user.type.isFederated
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observes the list of conversation members that are eligible to be promoted to admin role.
 *
 * A member is eligible if they are not the self user and satisfy all of:
 * - not federated
 * - not a bot/service
 * - has a non-empty name
 * - has a non-empty handle
 * - not a temporary/guest user (no expiry)
 */
public interface ObserveEligibleMembersForConversationAdminRoleUseCase {
    public suspend operator fun invoke(conversationId: ConversationId): Flow<List<MemberDetails>>
}

internal class ObserveEligibleMembersForConversationAdminRoleUseCaseImpl(
    private val observeConversationMembers: ObserveConversationMembersUseCase,
    private val selfUserId: UserId,
) : ObserveEligibleMembersForConversationAdminRoleUseCase {

    override suspend fun invoke(conversationId: ConversationId): Flow<List<MemberDetails>> =
        observeConversationMembers(conversationId).map { members ->
            members.filter { it.user.id != selfUserId && it.user.isEligibleForAdminPromotion() }
        }

    private fun User.isEligibleForAdminPromotion(): Boolean =
        !userType.isFederated() &&
                !userType.isAppOrBot() &&
                !name.isNullOrEmpty() &&
                !handle.isNullOrEmpty() &&
                expiresAt == null
}
