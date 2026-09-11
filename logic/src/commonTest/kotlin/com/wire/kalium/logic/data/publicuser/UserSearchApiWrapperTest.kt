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

package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.MemberDAOArrangementImpl
import com.wire.kalium.network.api.authenticated.search.ContactDTO
import com.wire.kalium.network.api.authenticated.search.SearchPolicyDTO
import com.wire.kalium.network.api.authenticated.search.UserSearchResponse
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.member.MemberEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import com.wire.kalium.network.api.model.UserId as UserIdDTO

class UserSearchApiWrapperTest {

    @Test
    fun givenNoConversationExcludedAndAllTeamsAndDomains_whenSearching_thenReturnsExpectedContacts() = runTest {
        verifySearch(
            searchUsersOptions = SearchUsersOptions(conversationMembersExcluded = null, onlySelfTeamAndDomain = false),
            expectedContacts = listOf(CONVERSATION_MEMBER, TEAMMATE, OTHER_TEAM_CONTACT, OTHER_DOMAIN_CONTACT, NO_TEAM_CONTACT)
        )
    }

    @Test
    fun givenConversationExcludedAndAllTeamsAndDomains_whenSearching_thenReturnsExpectedContacts() = runTest {
        verifySearch(
            searchUsersOptions = SearchUsersOptions(conversationMembersExcluded = CONVERSATION_ID, onlySelfTeamAndDomain = false),
            expectedContacts = listOf(TEAMMATE, OTHER_TEAM_CONTACT, OTHER_DOMAIN_CONTACT, NO_TEAM_CONTACT)
        )
    }

    @Test
    fun givenNoConversationExcludedAndOnlySelfTeamAndDomain_whenSearching_thenReturnsExpectedContacts() = runTest {
        verifySearch(
            searchUsersOptions = SearchUsersOptions(conversationMembersExcluded = null, onlySelfTeamAndDomain = true),
            expectedContacts = listOf(CONVERSATION_MEMBER, TEAMMATE)
        )
    }

    @Test
    fun givenConversationExcludedAndOnlySelfTeamAndDomain_whenSearching_thenReturnsExpectedContacts() = runTest {
        verifySearch(
            searchUsersOptions = SearchUsersOptions(conversationMembersExcluded = CONVERSATION_ID, onlySelfTeamAndDomain = true),
            expectedContacts = listOf(TEAMMATE)
        )
    }

    private suspend fun verifySearch(searchUsersOptions: SearchUsersOptions, expectedContacts: List<ContactDTO>) {
        val conversationMemberEntity = MemberEntity(
            user = UserId(CONVERSATION_MEMBER.qualifiedID.value, CONVERSATION_MEMBER.qualifiedID.domain).toDao(),
            role = MemberEntity.Role.Member
        )
        val (_, userSearchApiWrapper) = Arrangement()
            .withSuccessFullSearch(searchApiUsers = SEARCH_CONTACTS)
            .apply {
                if (searchUsersOptions.conversationMembersExcluded != null) {
                    withObserveConversationMembers(
                        result = flowOf(listOf(conversationMemberEntity)),
                        conversationId = { it == CONVERSATION_ID.toDao() }
                    )
                }
            }
            .arrange()

        val result = userSearchApiWrapper.search(
            searchQuery = "someQuery",
            domain = SELF_USER_ID.domain,
            maxResultSize = null,
            selfTeamId = SELF_TEAM_ID,
            searchUsersOptions = searchUsersOptions
        )

        assertIs<Either.Right<UserSearchResponse>>(result)
        assertEquals(expectedContacts, result.value.documents)
        assertEquals(expectedContacts.size, result.value.found)
        assertEquals(expectedContacts.size, result.value.returned)
    }

    private class Arrangement : MemberDAOArrangement by MemberDAOArrangementImpl() {
        private val userSearchApi: UserSearchApi = mock<UserSearchApi>(mode = MockMode.autoUnit)

        fun withSuccessFullSearch(searchApiUsers: List<ContactDTO>) = apply {
            everySuspend { userSearchApi.search(any()) } returns NetworkResponse.Success(
                value = UserSearchResponse(
                    documents = searchApiUsers,
                    found = searchApiUsers.size,
                    returned = searchApiUsers.size,
                    searchPolicy = SearchPolicyDTO.FULL_SEARCH,
                    took = 100
                ),
                headers = mapOf(),
                httpCode = 200
            )
        }

        fun arrange() = this to UserSearchApiWrapperImpl(userSearchApi, memberDAO, SELF_USER_ID)
    }

    private companion object {
        val SELF_USER_ID = UserId(value = "selfUserId", domain = "someDomain")
        val SELF_TEAM_ID = TeamId(value = "selfTeamId")
        val CONVERSATION_ID = ConversationId(value = "conversationId", domain = SELF_USER_ID.domain)
        val SELF_CONTACT = contact(id = SELF_USER_ID.value)
        val CONVERSATION_MEMBER = contact(id = "conversationMember")
        val TEAMMATE = contact(id = "teammate")
        val OTHER_TEAM_CONTACT = contact(id = "otherTeam", team = "otherTeamId")
        val OTHER_DOMAIN_CONTACT = contact(id = "otherDomain", domain = "otherDomain")
        val NO_TEAM_CONTACT = contact(id = "noTeam", team = null)
        val SEARCH_CONTACTS = listOf(SELF_CONTACT, CONVERSATION_MEMBER, TEAMMATE, OTHER_TEAM_CONTACT, OTHER_DOMAIN_CONTACT, NO_TEAM_CONTACT)

        fun contact(id: String, team: String? = SELF_TEAM_ID.value, domain: String = SELF_USER_ID.domain) = ContactDTO(
            accentId = null,
            handle = null,
            name = "",
            qualifiedID = UserIdDTO(value = id, domain = domain),
            team = team
        )
    }
}
