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

package com.wire.kalium.logic.feature.user

import app.cash.turbine.test
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.publicuser.ConversationMemberExcludedOptions
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUsersOptions
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.publicuser.search.SearchKnownUsersUseCase
import com.wire.kalium.logic.feature.publicuser.search.SearchKnownUsersUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.search.SearchUsersResult
import com.wire.kalium.logic.feature.publicuser.search.sanitizeHandleSearchPattern
import com.wire.kalium.logic.framework.TestUser
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SearchKnownUserUseCaseTest {

    @Test
    fun givenAnInputStartingWithAtSymbol_whenSearchingUsers_thenSearchOnlyByHandle() = runTest {
        // given
        val handleSearchQuery = "@somehandle"

        val (arrangement, searchKnownUsersUseCase) = Arrangement()
            .withSuccessFullSelfUserRetrieve()
            .withSearchByHandle(handleSearchQuery).arrange()

        // when
        searchKnownUsersUseCase(handleSearchQuery)
        // then
        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchKnownUsersByHandle)
            .with(eq(handleSearchQuery.removePrefix("@")), anything())
            .wasInvoked(exactly = once)

        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
            .with(anything(), anything())
            .wasNotInvoked()
    }

    @Test
    fun givenNormalInput_whenSearchingUsers_thenSearchByNameOrHandleOrEmail() = runTest {
        // given
        val searchQuery = "someSearchQuery"

        val (arrangement, searchKnownUsersUseCase) = Arrangement()
            .withSuccessFullSelfUserRetrieve()
            .withSearchKnownUsersByNameOrHandleOrEmail(searchQuery)
            .arrange()
        // when
        searchKnownUsersUseCase(searchQuery)
        // then
        with(arrangement) {
            verify(searchUserRepository)
                .suspendFunction(searchUserRepository::searchKnownUsersByHandle)
                .with(anything(), anything())
                .wasNotInvoked()

            verify(searchUserRepository)
                .suspendFunction(searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
                .with(eq(searchQuery.lowercase()), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenFederatedInput_whenSearchingUsers_thenSearchByNameOrHandleOrEmail() = runTest {
        // given
        val searchQuery = "someSearchQuery@wire.com"

        val (arrangement, searchKnownUsersUseCase) = Arrangement()
            .withSuccessFullSelfUserRetrieve()
            .withSearchKnownUsersByNameOrHandleOrEmail()
            .arrange()
        // when
        searchKnownUsersUseCase(searchQuery)
        // then
        with(arrangement) {
            verify(searchUserRepository)
                .suspendFunction(searchUserRepository::searchKnownUsersByHandle)
                .with(anything(), anything())
                .wasNotInvoked()

            verify(searchUserRepository)
                .suspendFunction(searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
                .with(anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun test() = runTest {
        // given
        val searchQuery = "someSearchQuery"

        val selfUserId = QualifiedID(
            value = "selfUser",
            domain = "wire.com",
        )

        val otherUserContainingSelfUserId = OtherUser(
            id = selfUserId,
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 0,
            teamId = null,
            connectionStatus = ConnectionState.ACCEPTED,
            previewPicture = null,
            completePicture = null,
            availabilityStatus = UserAvailabilityStatus.NONE,
            userType = UserType.EXTERNAL,
            botService = null,
            deleted = false,
            defederated = false,
            supportedProtocols = null
        )

        val (_, searchKnownUsersUseCase) = Arrangement()
            .withSuccessFullSelfUserRetrieve(selfUserId)
            .withSearchKnownUsersByNameOrHandleOrEmail(searchQuery, otherUserContainingSelfUserId)
            .arrange()
        // when

        searchKnownUsersUseCase(searchQuery).test {
            // then
            val result = awaitItem()
            assertIs<SearchUsersResult.Success>(result)
            assertFalse(result.userSearchResult.result.contains(otherUserContainingSelfUserId))
            awaitComplete()
        }

    }

    @Test
    fun givenSearchingForHandleWithConversationExcluded_whenSearchingUsers_ThenPropagateTheSearchOption() = runTest {
        // given
        val searchQuery = "@somehandle"

        val searchUsersOptions = SearchUsersOptions(
            ConversationMemberExcludedOptions.ConversationExcluded(
                ConversationId("someValue", "someDomain")
            ),
            selfUserIncluded = false
        )

        val (arrangement, searchKnownUsersUseCase) = Arrangement()
            .withSuccessFullSelfUserRetrieve()
            .withSearchByHandle(
                searchQuery = searchQuery,
                searchUsersOptions = searchUsersOptions
            ).withSearchKnownUsersByNameOrHandleOrEmail(
                searchQuery = searchQuery,
                searchUsersOptions = searchUsersOptions
            )
            .arrange()

        // when
        searchKnownUsersUseCase(
            searchQuery = searchQuery,
            searchUsersOptions = searchUsersOptions
        ).test {
            // then
            val result = awaitItem()
            assertIs<SearchUsersResult.Success>(result)
            verify(arrangement.searchUserRepository)
                .suspendFunction(arrangement.searchUserRepository::searchKnownUsersByHandle)
                .with(anything(), eq(searchUsersOptions))
                .wasInvoked(exactly = once)
            awaitComplete()
        }

    }

    @Test
    fun givenSearchingForNameOrHandleOrEmailWithConversationExcluded_whenSearchingUsers_ThenPropagateTheSearchOption() = runTest {
        // given
        val searchQuery = "someSearchQuery"

        val searchUsersOptions = SearchUsersOptions(
            ConversationMemberExcludedOptions.ConversationExcluded(
                ConversationId("someValue", "someDomain")
            ), selfUserIncluded = false
        )

        val (arrangement, searchKnownUsersUseCase) = Arrangement()
            .withSuccessFullSelfUserRetrieve()
            .withSearchKnownUsersByNameOrHandleOrEmail(
                searchQuery = searchQuery,
                searchUsersOptions = searchUsersOptions
            ).withSearchByHandle(
                searchQuery = searchQuery,
                searchUsersOptions = searchUsersOptions
            )
            .arrange()

        // when

        searchKnownUsersUseCase(
            searchQuery = searchQuery,
            searchUsersOptions = searchUsersOptions
        ).test {
            // then
            val result = awaitItem()
            assertIs<SearchUsersResult.Success>(result)
            awaitComplete()
            verify(arrangement.searchUserRepository)
                .suspendFunction(arrangement.searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
                .with(anything(), eq(searchUsersOptions))
                .wasInvoked(exactly = once)
        }

    }

    @Test
    fun givenAnInputStartingWithAtSymbolAndDomainPresent_whenSearchingUsers_thenSearchBySanitizedHandle() = runTest {
        // given
        val handlePartOfQuery = "somehandle"
        val handleSearchQuery = "@$handlePartOfQuery@bella.wire.link"
        val (arrangement, searchKnownUsersUseCase) = Arrangement()
            .withSuccessFullSelfUserRetrieve()
            .withSearchByHandle(handleSearchQuery)
            .arrange()

        // when
        searchKnownUsersUseCase(handleSearchQuery)

        // then
        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchKnownUsersByHandle)
            .with(eq(handlePartOfQuery), anything())
            .wasInvoked(exactly = once)

        verify(arrangement.searchUserRepository)
            .suspendFunction(arrangement.searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
            .with(anything(), anything())
            .wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val searchUserRepository = mock(classOf<SearchUserRepository>())

        @Mock
        val userRepository = mock(classOf<UserRepository>())

        @Mock
        val qualifiedIdMapper = mock(classOf<QualifiedIdMapper>())

        fun withSuccessFullSelfUserRetrieve(
            id: QualifiedID = QualifiedID(
                value = "selfUser",
                domain = "wire.com",
            )
        ): Arrangement {
            val selfUser = TestUser.SELF.copy(id = id)

            given(userRepository)
                .suspendFunction(userRepository::getSelfUser)
                .whenInvoked()
                .thenReturn(
                    selfUser
                )

            given(qualifiedIdMapper)
                .function(qualifiedIdMapper::fromStringToQualifiedID)
                .whenInvokedWith(eq("someSearchQuery@wire.com".lowercase()))
                .thenReturn(QualifiedID("someSearchQuery", "wire.com"))

            return this
        }

        fun withSearchByHandle(
            searchQuery: String? = null,
            searchUsersOptions: SearchUsersOptions? = null
        ): Arrangement {
            given(searchUserRepository)
                .suspendFunction(searchUserRepository::searchKnownUsersByHandle)
                .whenInvokedWith(
                    if (searchQuery == null) any() else eq(searchQuery.sanitizeHandleSearchPattern()),
                    if (searchUsersOptions == null) any() else eq(searchUsersOptions)
                )
                .thenReturn(
                    flowOf(
                        UserSearchResult(
                            listOf(
                                OtherUser(
                                    id = QualifiedID(
                                        value = "someValue",
                                        domain = "someDomain",
                                    ),
                                    name = null,
                                    handle = null,
                                    email = null,
                                    phone = null,
                                    accentId = 0,
                                    teamId = null,
                                    connectionStatus = ConnectionState.ACCEPTED,
                                    previewPicture = null,
                                    completePicture = null,
                                    availabilityStatus = UserAvailabilityStatus.NONE,
                                    userType = UserType.EXTERNAL,
                                    botService = null,
                                    deleted = false,
                                    defederated = false,
                                    supportedProtocols = null
                                )
                            )
                        )
                    )
                )

            return this
        }

        fun withSearchKnownUsersByNameOrHandleOrEmail(
            searchQuery: String? = null,
            extraOtherUser: OtherUser? = null,
            searchUsersOptions: SearchUsersOptions? = null
        ): Arrangement {
            val query = searchQuery?.lowercase()
            val otherUsers = listOf(
                OtherUser(
                    id = QualifiedID(
                        value = "someSearchQuery",
                        domain = "wire.com",
                    ),
                    name = null,
                    handle = null,
                    email = null,
                    phone = null,
                    accentId = 0,
                    teamId = null,
                    connectionStatus = ConnectionState.ACCEPTED,
                    previewPicture = null,
                    completePicture = null,
                    availabilityStatus = UserAvailabilityStatus.NONE,
                    userType = UserType.FEDERATED,
                    botService = null,
                    deleted = false,
                    defederated = false,
                    supportedProtocols = null
                )
            )

            if (extraOtherUser != null) {
                otherUsers.plus(extraOtherUser)
            }

            given(searchUserRepository)
                .suspendFunction(searchUserRepository::searchKnownUsersByNameOrHandleOrEmail)
                .whenInvokedWith(
                    if (query == null) any() else eq(query.removePrefix("@")),
                    if (searchUsersOptions == null) any() else eq(searchUsersOptions)
                )
                .thenReturn(flowOf(UserSearchResult(otherUsers)))

            return this
        }

        fun arrange(): Pair<Arrangement, SearchKnownUsersUseCase> {
            return this to SearchKnownUsersUseCaseImpl(searchUserRepository, userRepository, qualifiedIdMapper)
        }
    }
}
