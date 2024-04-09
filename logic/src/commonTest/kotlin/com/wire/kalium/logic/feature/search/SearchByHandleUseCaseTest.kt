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
import io.mockative.anything
import io.mockative.eq
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchByHandleUseCaseTest {

    @Test
    fun givenEmptySearchQueryAndNoExcludedConversation_whenInvokingSearch_thenReturnEmptySearchResult() = runTest {

        val (arrangement, searchUseCase) = Arrangement().arrange {
            withGetKnownContacts(
                result = emptyList<UserSearchDetails>().right(),
                excludeConversation = eq(null)
            )
        }

        val result = searchUseCase(
            searchHandle = "",
            excludingConversation = null,
            customDomain = null
        )

        assertEquals(
            expected = emptyList<UserSearchDetails>(),
            actual = result.connected
        )


        assertEquals(
            expected = emptyList<UserSearchDetails>(),
            actual = result.notConnected
        )
        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::getKnownContacts)
            .with(eq(null))
            .wasNotInvoked()

        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchUserRemoteDirectory)
            .with(any(), anything())
            .wasNotInvoked()
    }

    @Test
    fun givenEmptySearchQueryWithExcludedConversation_whenInvokingSearch_thenReturnEmptySearchResult() = runTest {

        val conversationId = ConversationId("conversationId", "conversationDomain")
        val (arrangement, searchUseCase) = Arrangement().arrange { }

        val result = searchUseCase(
            searchHandle = "",
            excludingConversation = conversationId,
            customDomain = null
        )

        assertEquals(
            expected = emptyList<UserSearchDetails>(),
            actual = result.connected
        )

        assertEquals(
            expected = emptyList<UserSearchDetails>(),
            actual = result.notConnected
        )

        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::getKnownContacts)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchUserRemoteDirectory)
            .with(any(), anything())
            .wasNotInvoked()
    }

    @Test
    fun givenNonEmptySearchQueryAndNoExcludedConversation_whenInvokingSearch_thenRespondWithAllKnownContacts() = runTest {

        val (arrangement, searchUseCase) = Arrangement().arrange {
            withSearchByHandle(
                result = emptyList<UserSearchDetails>().right(),
            )
            withSearchUserRemoteDirectory(
                result = UserSearchResult(emptyList()).right(),
            )
        }

        val result = searchUseCase(
            searchHandle = "searchQuery",
            excludingConversation = null,
            customDomain = null
        )

        assertEquals(
            expected = emptyList<UserSearchDetails>(),
            actual = result.connected
        )
        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchUserRemoteDirectory)
            .with(eq("searchquery"), any(), any(), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenLocalAndRemoteResult_whenInvokingSearch_thenThereAreNoDuplicatedResult() = runTest {

        val remoteSearchResult = listOf(
            newOtherUser("remoteAndLocalUser1").copy(name = "updatedNewName", connectionStatus = ConnectionState.NOT_CONNECTED),
            newOtherUser("remoteUser2").copy(
                teamId = TeamId("otherTeamId"),
                connectionStatus = ConnectionState.PENDING
            ),
            newOtherUser("remoteUser3").copy(
                teamId = TeamId("otherTeamId"),
                connectionStatus = ConnectionState.ACCEPTED
            ),
        )

        val localSearchResult = listOf(
            newUserSearchDetails("remoteAndLocalUser1").copy(name = "oldName"),
            newUserSearchDetails("localUser2")
        )

        val expected = SearchUserResult(
            connected = listOf(
                newUserSearchDetails("remoteAndLocalUser1").copy(name = "oldName"),
                newUserSearchDetails("localUser2"),
                newUserSearchDetails("remoteUser3")
            ),
            notConnected = listOf(
                newUserSearchDetails("remoteUser2").copy(connectionStatus = ConnectionState.PENDING),
            )
        )

        val (arrangement, searchUseCase) = Arrangement().arrange {
            withSearchUserRemoteDirectory(
                result = UserSearchResult(remoteSearchResult).right(),
                searchQuery = eq("searchquery"),
            )
            withSearchByHandle(
                result = localSearchResult.right(),
                searchQuery = eq("searchquery"),
            )
        }

        val result = searchUseCase(
            searchHandle = "searchQuery",
            excludingConversation = null,
            customDomain = null
        )

        assertEquals(
            expected = expected,
            actual = result
        )
        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchUserRemoteDirectory)
            .with(eq("searchquery"), any(), any(), any())
            .wasInvoked(exactly = once)
        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchLocalByHandle)
            .with(eq("searchquery"), anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSearchQuery_whenDoingSearch_thenCallTheSearchFunctionsWithCleanQuery() = runTest {
        val searchQuery = "    @search Query     "
        val cleanQuery = "search query"
        val (arrangement, searchUseCase) = Arrangement().arrange {
            withSearchUserRemoteDirectory(
                result = UserSearchResult(emptyList()).right(),
                searchQuery = eq(cleanQuery),
            )
            withSearchByHandle(
                result = emptyList<UserSearchDetails>().right(),
                searchQuery = eq(cleanQuery),
            )
        }

        val result = searchUseCase(
            searchHandle = searchQuery,
            excludingConversation = null,
            customDomain = null
        )

        assertEquals(
            expected = emptyList<UserSearchDetails>(),
            actual = result.connected
        )
        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchUserRemoteDirectory)
            .with(eq(cleanQuery), any(), any(), any())
            .wasInvoked(exactly = once)
        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchLocalByHandle)
            .with(eq(cleanQuery), anything())
            .wasInvoked(exactly = once)
    }

    private companion object {
        val selfUserId = UserId("self", "domain")

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
        private val useCase: SearchByHandleUseCase by lazy {
            SearchByHandleUseCase(
                searchUserRepository = searchUserRepository,
                selfUserId = selfUserId,
                maxRemoteSearchResultCount = 30
            )
        }

        suspend fun arrange(block: Arrangement.() -> Unit) = apply(block).let {
            this to useCase
        }
    }
}
