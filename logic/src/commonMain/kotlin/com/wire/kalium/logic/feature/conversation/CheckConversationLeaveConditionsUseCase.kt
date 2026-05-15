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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.flow.firstOrNull

/**
 * Checks whether the self user is allowed to leave the given group conversation.
 *
 * Returns [Result.Allow] when leaving is safe, [Result.DoNotAllow] when self is the sole admin
 * and other members remain (with [Result.DoNotAllow.eligibleUsersAvailable] indicating whether
 * any of them can be promoted), or [Result.Error] on a storage failure.
 */
public interface CheckConversationLeaveConditionsUseCase {
    public suspend operator fun invoke(conversationId: ConversationId): Result

    public sealed interface Result {
        public data object Allow : Result
        public data class DoNotAllow(val eligibleUsersAvailable: Boolean) : Result
        public data class Error(val cause: CoreFailure) : Result
    }
}

internal class CheckConversationLeaveConditionsUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val observeEligibleMembers: ObserveEligibleMembersForConversationAdminRoleUseCase,
    private val selfUserId: UserId,
) : CheckConversationLeaveConditionsUseCase {

    override suspend fun invoke(conversationId: ConversationId): CheckConversationLeaveConditionsUseCase.Result {
        val members = conversationRepository.observeConversationMembers(conversationId).firstOrNull()
        return when {
            members.isNullOrEmpty() -> CheckConversationLeaveConditionsUseCase.Result.Error(StorageFailure.DataNotFound)
            isSelfUserNotAdmin(members) -> CheckConversationLeaveConditionsUseCase.Result.Allow
            groupHasOtherAdmins(members) -> CheckConversationLeaveConditionsUseCase.Result.Allow
            noOtherMembers(members) -> CheckConversationLeaveConditionsUseCase.Result.Allow
            else -> {
                val eligibleUsersAvailable = observeEligibleMembers(conversationId).firstOrNull().isNullOrEmpty().not()
                CheckConversationLeaveConditionsUseCase.Result.DoNotAllow(eligibleUsersAvailable)
            }
        }
    }

    private fun isSelfUserNotAdmin(members: List<Conversation.Member>) =
        members.find { it.id == selfUserId }?.role !is Conversation.Member.Role.Admin

    private fun groupHasOtherAdmins(members: List<Conversation.Member>) =
        members.count { it.role is Conversation.Member.Role.Admin } > 1

    private fun noOtherMembers(members: List<Conversation.Member>) = members.size == 1
}
