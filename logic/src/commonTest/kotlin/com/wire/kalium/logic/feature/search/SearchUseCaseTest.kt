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
package com.wire.kalium.logic.feature.search

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.publicuser.model.UserSearchDetails
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.util.arrangement.repository.SearchRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.SearchRepositoryArrangementImpl
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.EqualsMatcher
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchUseCaseTest {

    @Test
    fun givenEmptySearchQueryAndNoExcludedConversation_whenInvokingSearch_thenRespondWithAllKnownContacts() = runTest {
        val (arrangement, searchUseCase) = Arrangement().arrange {
            withGetKnownContacts(
                result = emptyList<UserSearchDetails>().right()
            )
        }

        val result = searchUseCase(
            searchQuery = "",
            excludingMembersOfConversation = null,
            customDomain = null
        )

        assertEquals(
            expected = emptyList(),
            actual = result.connected
        )
        coVerify {
            arrangement.searchUserRepository.getKnownContacts(null)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenEmptySearchQueryAndExcludedConversation_whenInvokingSearch_thenRespondWithAllKnownContacts() = runTest {

        val conversationId = ConversationId("conversationId", "conversationDomain")
        val (arrangement, searchUseCase) = Arrangement().arrange {
            withGetKnownContacts(
                result = emptyList<UserSearchDetails>().right()
            )
        }

        val result = searchUseCase(
            searchQuery = "",
            excludingMembersOfConversation = conversationId,
            customDomain = null
        )

        assertEquals(
            expected = emptyList(),
            actual = result.connected
        )
        coVerify {
            arrangement.searchUserRepository.getKnownContacts(eq(conversationId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenNonEmptySearchQueryAndNoExcludedConversation_whenInvokingSearch_thenRespondWithAllKnownContacts() = runTest {
        val (arrangement, searchUseCase) = Arrangement().arrange {
            withSearchLocalByName(
                result = emptyList<UserSearchDetails>().right(),
            )
            withSearchUserRemoteDirectory(
                result = UserSearchResult(emptyList()).right(),
            )
        }

        val result = searchUseCase(
            searchQuery = "searchQuery",
            excludingMembersOfConversation = null,
            customDomain = null
        )

        assertEquals(
            expected = emptyList(),
            actual = result.connected
        )
        coVerify {
            arrangement.searchUserRepository.searchUserRemoteDirectory(eq("searchquery"), any(), any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenLocalAndRemoteResult_whenInvokingSearch_thenThereAreNoDuplicatedResult() = runTest {

        val remoteSearchResult = listOf(
            newOtherUser("remoteAndLocalUser1").copy(name = "updatedNewName"),
            newOtherUser("remoteUser2").copy(
                teamId = TeamId("otherTeamId"),
                connectionStatus = ConnectionState.PENDING
            ),
        )

        val localSearchResult = listOf(
            newUserSearchDetails("remoteAndLocalUser1").copy(name = "oldName"),
            newUserSearchDetails("localUser2")
        )

        val expected = SearchUserResult(
            connected = listOf(
                newUserSearchDetails("remoteAndLocalUser1").copy(name = "updatedNewName"),
                newUserSearchDetails("localUser2")
            ),
            notConnected = listOf(
                newUserSearchDetails("remoteUser2").copy(connectionStatus = ConnectionState.PENDING),
            )
        )

        val (arrangement, searchUseCase) = Arrangement().arrange {
            withSearchUserRemoteDirectory(
                result = UserSearchResult(remoteSearchResult).right(),
                searchQuery = EqualsMatcher("searchquery"),
            )
            withSearchLocalByName(
                result = localSearchResult.right(),
                searchQuery = EqualsMatcher("searchquery"),
            )
        }

        val result = searchUseCase(
            searchQuery = "searchQuery",
            excludingMembersOfConversation = null,
            customDomain = null
        )

        assertEquals(
            expected = expected,
            actual = result
        )
        coVerify {
            arrangement.searchUserRepository.searchUserRemoteDirectory(eq("searchquery"), any(), any(), any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.searchUserRepository.searchLocalByName(eq("searchquery"), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSearchQuery_whenDoingSearch_thenCallTheSearchFunctionsWithCleanQuery() = runTest {
        val searchQuery = "    search Query     "
        val cleanQuery = "search query"
        val (arrangement, searchUseCase) = Arrangement().arrange {
            withSearchUserRemoteDirectory(
                result = UserSearchResult(emptyList()).right(),
                searchQuery = EqualsMatcher(cleanQuery),
            )
            withSearchLocalByName(
                result = emptyList<UserSearchDetails>().right(),
                searchQuery = EqualsMatcher(cleanQuery),
            )
        }

        val result = searchUseCase(
            searchQuery = searchQuery,
            excludingMembersOfConversation = null,
            customDomain = null
        )

        assertEquals(
            expected = emptyList(),
            actual = result.connected
        )
        coVerify {
            arrangement.searchUserRepository.searchUserRemoteDirectory(eq(cleanQuery), any(), any(), any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.searchUserRepository.searchLocalByName(eq(cleanQuery), any())
        }.wasInvoked(exactly = once)
    }

    private companion object {

        val selfUserID = UserId("searchUserID", "searchUserDomain")

        fun newOtherUser(id: String) = OtherUser(
            UserId(id, "otherDomain"),
            name = "otherUsername",
            handle = "handle",
            email = "otherEmail",
            phone = "otherPhone",
            accentId = 0,
            teamId = TeamId("otherTeamId"),
            connectionStatus = ConnectionState.ACCEPTED,
            previewPicture = UserAssetId("value", "domain"),
            completePicture = UserAssetId("value", "domain"),
            availabilityStatus = UserAvailabilityStatus.AVAILABLE,
            userType = UserType.INTERNAL,
            botService = null,
            deleted = false,
            defederated = false,
            isProteusVerified = false,
            supportedProtocols = setOf(SupportedProtocol.PROTEUS)
        )

        fun newUserSearchDetails(id: String) = UserSearchDetails(
            id = UserId(id, "otherDomain"),
            name = "otherUsername",
            previewAssetId = UserAssetId("value", "domain"),
            completeAssetId = UserAssetId("value", "domain"),
            type = UserType.INTERNAL,
            connectionStatus = ConnectionState.ACCEPTED,
            handle = "handle"
        )
    }

    private class Arrangement : SearchRepositoryArrangement by SearchRepositoryArrangementImpl() {

        private val searchUseCase: SearchUsersUseCase = SearchUsersUseCaseImpl(
            searchUserRepository = searchUserRepository,
            selfUserId = selfUserID,
            maxRemoteSearchResultCount = 30
        )

        fun arrange(block: suspend Arrangement.() -> Unit) = apply {
            runBlocking { block() }
        }.run { this to searchUseCase }
    }
}
