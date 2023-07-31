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
import com.wire.kalium.logic.framework.TestUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddingMembersFailureMapperTest {

    private val addingMembersFailureMapper = AddingMembersFailureMapperImpl()

    @Test
    fun givenUsersIdsAndDomainsError_whenMapping_thenShouldSplitIntoValidAndNotValidUserIds() {
        // When
        val mappedRequestState = addingMembersFailureMapper.mapToUsersRequestState(
            initialUsersIds = initialUserIdList,
            federatedBackendFailure = NetworkFailure.FederatedBackendFailure.FailedDomains(unreachableDomains)
        )

        // Then
        assertEquals(TestUser.OTHER_USER_ID, mappedRequestState.usersThatCanBeAdded.first())
        assertEquals(TestUser.OTHER_FEDERATED_USER_ID, mappedRequestState.usersThatCannotBeAdded.first())
    }

    @Test
    fun givenUsersIdsADomainsErrorAndPreviousFailedIds_whenMapping_thenShouldSplitConsideringPreviousFailedIds() {
        // When
        val mappedRequestState = addingMembersFailureMapper.mapToUsersRequestState(
            initialUsersIds = initialUserIdList,
            federatedBackendFailure = NetworkFailure.FederatedBackendFailure.FailedDomains(unreachableDomains),
            previousUserIdsExcluded = previousFailedIds
        )

        // Then
        assertEquals(TestUser.OTHER_USER_ID, mappedRequestState.usersThatCanBeAdded.first())
        assertEquals(2, mappedRequestState.usersThatCannotBeAdded.size)
        assertTrue {
            mappedRequestState.usersThatCannotBeAdded.containsAll(
                listOf(
                    TestUser.OTHER_FEDERATED_USER_ID,
                    TestUser.OTHER_FEDERATED_USER_ID_2
                )
            )
        }
    }

    private companion object {
        val initialUserIdList = listOf(TestUser.OTHER_USER_ID, TestUser.OTHER_FEDERATED_USER_ID)
        val previousFailedIds = setOf(TestUser.OTHER_FEDERATED_USER_ID_2)
        val unreachableDomains = listOf("otherDomain")
    }
}
