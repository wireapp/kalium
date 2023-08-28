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
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.publicuser.ConversationMemberExcludedOptions
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUsersOptions
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.publicuser.search.SearchPublicUsersUseCase
import com.wire.kalium.logic.feature.publicuser.search.SearchPublicUsersUseCaseImpl
import com.wire.kalium.logic.feature.publicuser.search.SearchUsersResult
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.Times
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

@ExperimentalCoroutinesApi
class SearchUserUseCaseTest {

    @Mock
    private val searchUserRepository = mock(classOf<SearchUserRepository>())

    @Mock
    private val connectionRepository = mock(classOf<ConnectionRepository>())

    @Mock
    private val qualifiedIdMapper = mock(classOf<QualifiedIdMapper>())

    private lateinit var searchPublicUsersUseCase: SearchPublicUsersUseCase

    @BeforeTest
    fun setUp() {
        searchPublicUsersUseCase = SearchPublicUsersUseCaseImpl(searchUserRepository, connectionRepository, qualifiedIdMapper)

        given(connectionRepository)
            .suspendFunction(connectionRepository::observeConnectionList)
            .whenInvoked()
            .thenReturn(flowOf(listOf()))

        given(qualifiedIdMapper)
            .function(qualifiedIdMapper::fromStringToQualifiedID)
            .whenInvokedWith(eq(TEST_QUERY))
            .thenReturn(QualifiedID(TEST_QUERY, ""))

        given(qualifiedIdMapper)
            .function(qualifiedIdMapper::fromStringToQualifiedID)
            .whenInvokedWith(eq(TEST_QUERY_FEDERATED))
            .thenReturn(QualifiedID(TEST_QUERY, "wire.com"))

    }

    @Test
    fun givenValidParams_whenSearchingPublicUser_thenCorrectlyPropagateSuccessResult() = runTest {
        // given
        val expected = Either.Right(VALID_SEARCH_PUBLIC_RESULT)

        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchUserDirectory)
            .whenInvokedWith(anything(), anything(), anything(), anything())
            .thenReturn(expected)

        // when
        searchPublicUsersUseCase(TEST_QUERY).test {
            // then
            val actual = awaitItem()
            assertIs<SearchUsersResult.Success>(actual)
            assertContentEquals(expected.value.result, actual.userSearchResult.result)
            awaitComplete()
        }
    }

    @Test
    fun givenPendingConnectionRequests_whenSearchingPublicUser_thenCorrectlyPropagateUserWithConnectionStatus() = runTest {
        // given
        val expected = Either.Right(VALID_SEARCH_PUBLIC_RESULT)

        given(connectionRepository)
            .suspendFunction(connectionRepository::observeConnectionList)
            .whenInvoked()
            .thenReturn(flowOf(listOf(PENDING_CONNECTION)))

        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchUserDirectory)
            .whenInvokedWith(anything(), anything(), anything(), anything())
            .thenReturn(expected)

        // when
        searchPublicUsersUseCase(TEST_QUERY).test {
            // then
            val actual = awaitItem()
            assertIs<SearchUsersResult.Success>(actual)
            assertEquals(
                actual.userSearchResult.result.first { it.id == PENDING_CONNECTION.qualifiedToId }.connectionStatus,
                ConnectionState.PENDING
            )
            awaitComplete()
        }
    }

    @Test
    fun givenValidParams_federated_whenSearchingPublicUser_thenCorrectlyPropagateSuccessResult() = runTest {
        // given
        val expected = Either.Right(VALID_SEARCH_PUBLIC_RESULT)

        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchUserDirectory)
            .whenInvokedWith(eq("testQuery"), eq("wire.com"), anything(), anything())
            .thenReturn(expected)

        // when
        searchPublicUsersUseCase(TEST_QUERY_FEDERATED).test {
            // then
            val actual = awaitItem()
            assertIs<SearchUsersResult.Success>(actual)
            assertEquals(expected.value, actual.userSearchResult)
            awaitComplete()
        }
    }

    @Test
    fun givenFailure_whenSearchingPublicUser_thenCorrectlyPropagateFailureResult() = runTest {
        // given
        val expected = TEST_CORE_FAILURE

        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchUserDirectory)
            .whenInvokedWith(eq("testQuery"), eq(""), anything(), anything())
            .thenReturn(expected)

        // when
        searchPublicUsersUseCase(TEST_QUERY).test {
            // then
            assertIs<SearchUsersResult.Failure.InvalidQuery>(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenNoSearchOptionSpecific_whenSearchingPublicUser_thenCorrectlyPropagateDefaultSearchOption() = runTest {
        // given
        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchUserDirectory)
            .whenInvokedWith(anything(), anything(), anything(), anything())
            .thenReturn(Either.Right(VALID_SEARCH_PUBLIC_RESULT))

        // when
        searchPublicUsersUseCase(TEST_QUERY).test {
            // then
            awaitItem()
            verify(searchUserRepository)
                .suspendFunction(searchUserRepository::searchUserDirectory)
                .with(anything(), anything(), anything(), eq(SearchUsersOptions.Default))
                .wasInvoked(Times(1))
            awaitComplete()
        }
    }

    @Test
    fun givenSearchOptionSpecified_whenSearchingPublicUser_thenCorrectlyPropagateSearchOption() = runTest {
        // given
        val givenSearchUsersOptions = SearchUsersOptions(
            conversationExcluded = ConversationMemberExcludedOptions.ConversationExcluded(
                ConversationId(
                    "someValue",
                    "someDomain"
                )
            ),
            selfUserIncluded = false
        )

        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchUserDirectory)
            .whenInvokedWith(
                anything(),
                anything(),
                anything(),
                eq(givenSearchUsersOptions)
            )
            .thenReturn(Either.Right(VALID_SEARCH_PUBLIC_RESULT))

        // when
        searchPublicUsersUseCase(searchQuery = TEST_QUERY, searchUsersOptions = givenSearchUsersOptions).test {
            // then
            awaitItem()
            verify(searchUserRepository)
                .suspendFunction(searchUserRepository::searchUserDirectory)
                .with(anything(), anything(), anything(), eq(givenSearchUsersOptions))
                .wasInvoked(Times(1))
            awaitComplete()
        }
    }

    private companion object {
        const val TEST_QUERY = "testQuery"
        const val TEST_QUERY_FEDERATED = "testQuery@wire.com"

        val TEST_CORE_FAILURE = Either.Left(
            NetworkFailure.ServerMiscommunication(KaliumException.InvalidRequestError(ErrorResponse(404, "a", "")))
        )

        val PENDING_CONNECTION = Connection(
            "someId",
            "from",
            "lastUpdate",
            QualifiedID("conversationId", "someDomain"),
            UserId(0.toString(), "domain0"),
            ConnectionState.PENDING,
            "toId",
            null
        )

        val VALID_SEARCH_PUBLIC_RESULT = UserSearchResult(
            result = MutableList(size = 5) {
                OtherUser(
                    id = UserId(it.toString(), "domain$it"),
                    name = "name$it",
                    handle = null,
                    email = null,
                    phone = null,
                    accentId = it,
                    teamId = null,
                    connectionStatus = ConnectionState.NOT_CONNECTED,
                    previewPicture = null,
                    completePicture = null,
                    availabilityStatus = UserAvailabilityStatus.NONE,
                    userType = UserType.FEDERATED,
                    botService = null,
                    deleted = false,
                    defederated = false
                )
            }
        )
    }

}
