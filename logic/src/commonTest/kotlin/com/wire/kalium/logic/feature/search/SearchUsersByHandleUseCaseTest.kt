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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUsersOptions
import com.wire.kalium.logic.data.publicuser.model.UserSearchDetails
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.publicuser.ConversationMemberExcludedOptions
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchUsersByHandleUseCaseTest {

    @Test
    fun givenEmptySearchQueryAndNoExcludedConversation_whenInvokingSearch_thenReturnEmptySearchResult() = runTest {
        val (arrangement, searchUseCase) = Arrangement().arrange {
            withGetKnownContacts(
                result = emptyList<UserSearchDetails>().right(),
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
        verifySuspend(VerifyMode.not) {
            arrangement.searchUserRepository.getKnownContacts(excludeConversation = null)
        }

        verifySuspend(VerifyMode.not) {
            arrangement.searchUserRepository.searchUserRemoteDirectory(
                searchQuery = any(), domain = any(), maxResultSize = any(), searchUsersOptions = any()
            )
        }
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

        verifySuspend(VerifyMode.not) {
            arrangement.searchUserRepository.getKnownContacts(excludeConversation = any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.searchUserRepository.searchUserRemoteDirectory(
                searchQuery = any(), domain = any(), maxResultSize = any(), searchUsersOptions = any()
            )
        }
    }

    @Test
    fun givenNonEmptySearchQueryAndNoExcludedConversation_whenInvokingSearch_thenSearchRemoteAndLocalNotExcludingAnyConversation() = runTest {
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

        val expectedSearchUsersOptions = SearchUsersOptions(
            conversationExcluded = ConversationMemberExcludedOptions.None,
            selfUserIncluded = false
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.searchUserRepository.searchUserRemoteDirectory(
                searchQuery = "searchquery", domain = any(), maxResultSize = any(), searchUsersOptions = expectedSearchUsersOptions
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.searchUserRepository.searchLocalByHandle(handle = "searchquery", excludeMembersOfConversation = null)
        }
    }

    @Test
    fun givenNonEmptySearchQueryAndExcludedConversation_whenInvokingSearch_thenSearchRemoteAndLocalExcludingConversationId() = runTest {
        val conversationId = ConversationId("conversationId", "conversationDomain")
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
            excludingConversation = conversationId,
            customDomain = null
        )

        assertEquals(
            expected = emptyList<UserSearchDetails>(),
            actual = result.connected
        )

        val expectedSearchUsersOptions = SearchUsersOptions(
            conversationExcluded = ConversationMemberExcludedOptions.ConversationExcluded(conversationId),
            selfUserIncluded = false
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.searchUserRepository.searchUserRemoteDirectory(
                searchQuery = "searchquery", domain = any(), maxResultSize = any(), searchUsersOptions = expectedSearchUsersOptions
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.searchUserRepository.searchLocalByHandle(handle = "searchquery", excludeMembersOfConversation = conversationId)
        }
    }

    @Test
    fun givenEmptySearchQueryAndNoExcludedNotConnected_whenInvokingSearch_thenReturnEmptySearchResult() = runTest {
        val (arrangement, searchUseCase) = Arrangement().arrange {
            withGetKnownContacts(
                result = emptyList<UserSearchDetails>().right(),
            )
        }

        val result = searchUseCase(
            searchHandle = "",
            excludingConversation = null,
            excludingNotConnected = false,
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
        verifySuspend(VerifyMode.not) {
            arrangement.searchUserRepository.getKnownContacts(excludeConversation = null)
        }

        verifySuspend(VerifyMode.not) {
            arrangement.searchUserRepository.searchUserRemoteDirectory(
                searchQuery = any(), domain = any(), maxResultSize = any(), searchUsersOptions = any()
            )
        }
    }

    @Test
    fun givenEmptySearchQueryAndExcludedNotConnected_whenInvokingSearch_thenReturnEmptySearchResult() = runTest {
        val (arrangement, searchUseCase) = Arrangement().arrange { }

        val result = searchUseCase(
            searchHandle = "",
            excludingConversation = null,
            excludingNotConnected = true,
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

        verifySuspend(VerifyMode.not) {
            arrangement.searchUserRepository.getKnownContacts(excludeConversation = any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.searchUserRepository.searchUserRemoteDirectory(
                searchQuery = any(), domain = any(), maxResultSize = any(), searchUsersOptions = any()
            )
        }
    }

    @Test
    fun givenNonEmptySearchQueryAndNotExcludedNotConnected_whenInvokingSearch_thenSearchRemoteAndLocal() = runTest {
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
            excludingNotConnected = false,
            customDomain = null
        )

        assertEquals(
            expected = emptyList<UserSearchDetails>(),
            actual = result.connected
        )

        val expectedSearchUsersOptions = SearchUsersOptions(
            conversationExcluded = ConversationMemberExcludedOptions.None,
            selfUserIncluded = false
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.searchUserRepository.searchUserRemoteDirectory(
                searchQuery = "searchquery", domain = any(), maxResultSize = any(), searchUsersOptions = expectedSearchUsersOptions
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.searchUserRepository.searchLocalByHandle(handle = "searchquery", excludeMembersOfConversation = null)
        }
    }

    @Test
    fun givenNonEmptySearchQueryAndExcludedNotConnected_whenInvokingSearch_thenSearchLocalOnly() = runTest {
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
            excludingNotConnected = true,
            customDomain = null
        )

        assertEquals(
            expected = emptyList<UserSearchDetails>(),
            actual = result.connected
        )

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.searchUserRepository.searchUserRemoteDirectory(
                searchQuery = "searchquery", domain = any(), maxResultSize = any(), searchUsersOptions = any()
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.searchUserRepository.searchLocalByHandle(handle = "searchquery", excludeMembersOfConversation = null)
        }
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
                searchQuery = "searchquery",
            )
            withSearchByHandle(
                result = localSearchResult.right(),
                searchQuery = "searchquery",
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.searchUserRepository.searchUserRemoteDirectory(
                searchQuery = "searchquery", domain = any(), maxResultSize = any(), searchUsersOptions = any()
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.searchUserRepository.searchLocalByHandle(handle = "searchquery", excludeMembersOfConversation = any())
        }
    }

    @Test
    fun givenSearchQuery_whenDoingSearch_thenCallTheSearchFunctionsWithCleanQuery() = runTest {
        val searchQuery = "    @search Query     "
        val cleanQuery = "search query"
        val (arrangement, searchUseCase) = Arrangement().arrange {
            withSearchUserRemoteDirectory(
                result = UserSearchResult(emptyList()).right(),
                searchQuery = cleanQuery,
            )
            withSearchByHandle(
                result = emptyList<UserSearchDetails>().right(),
                searchQuery = cleanQuery,
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.searchUserRepository.searchUserRemoteDirectory(
                searchQuery = cleanQuery, domain = any(), maxResultSize = any(), searchUsersOptions = any()
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.searchUserRepository.searchLocalByHandle(handle = cleanQuery, excludeMembersOfConversation = any())
        }
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
            userType = UserTypeInfo.Regular(UserType.INTERNAL),
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
            type = UserTypeInfo.Regular(UserType.INTERNAL),
            connectionStatus = ConnectionState.ACCEPTED,
            handle = "handle"
        )
    }

    private class Arrangement {
        val searchUserRepository = mock<SearchUserRepository>(mode = MockMode.autoUnit)
        private val useCase: SearchByHandleUseCase by lazy {
            SearchByHandleUseCaseImpl(
                searchUserRepository = searchUserRepository,
                selfUserId = selfUserId,
                maxRemoteSearchResultCount = 30
            )
        }

        suspend fun arrange(block: suspend Arrangement.() -> Unit) = let {
            block()
            this to useCase
        }

        fun withSearchUserRemoteDirectory(
            result: Either<CoreFailure, UserSearchResult>,
            searchQuery: String? = null
        ) {
            everySuspend {
                searchUserRepository.searchUserRemoteDirectory(
                    searchQuery = searchQuery ?: any(),
                    domain = any(),
                    maxResultSize = any(),
                    searchUsersOptions = any()
                )
            } returns result
        }

        fun withGetKnownContacts(result: Either<StorageFailure, List<UserSearchDetails>>) {
            everySuspend { searchUserRepository.getKnownContacts(excludeConversation = any()) } returns result
        }

        fun withSearchByHandle(
            result: Either<StorageFailure, List<UserSearchDetails>>,
            searchQuery: String? = null
        ) {
            everySuspend {
                searchUserRepository.searchLocalByHandle(handle = searchQuery ?: any(), excludeMembersOfConversation = any())
            } returns result
        }
    }
}
