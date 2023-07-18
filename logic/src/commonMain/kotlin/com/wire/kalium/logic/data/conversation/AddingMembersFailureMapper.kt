/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserId

internal interface AddingMembersFailureMapper {
    /**
     * Will map the [initialUsersIds] and split accordingly excluding users with domain failures.
     * @param initialUsersIds the list of users that were initially requested to be added to the conversation
     * @param federatedBackendFailure the [NetworkFailure.FederatedBackendFailure] that contains the domains that failed
     * @param previousUserIdsExcluded the previous attempt of list of users that cannot be added to the conversation
     */
    fun mapToUsersRequestState(
        initialUsersIds: List<UserId>,
        federatedBackendFailure: NetworkFailure.FederatedBackendFailure,
        previousUserIdsExcluded: Set<UserId> = emptySet(),
    ): AddingMembersRequestState
}

internal class AddingMembersFailureMapperImpl : AddingMembersFailureMapper {
    override fun mapToUsersRequestState(
        initialUsersIds: List<UserId>,
        federatedBackendFailure: NetworkFailure.FederatedBackendFailure,
        previousUserIdsExcluded: Set<UserId>
    ): AddingMembersRequestState {
        val domainsToExclude = federatedBackendFailure.domains
        // splitting the initialUsersIds into users with failures[true] and users without failures[false]
        val groupedUsersWithFailure = initialUsersIds.groupBy {
            domainsToExclude.contains(it.domain)
        }
        return AddingMembersRequestState(
            usersThatCanBeAdded = groupedUsersWithFailure[false]?.toSet().orEmpty(),
            usersThatCannotBeAdded = groupedUsersWithFailure[true]?.toSet().orEmpty() + previousUserIdsExcluded
        )
    }
}

data class AddingMembersRequestState(
    val usersThatCanBeAdded: Set<UserId>,
    val usersThatCannotBeAdded: Set<UserId>,
)
