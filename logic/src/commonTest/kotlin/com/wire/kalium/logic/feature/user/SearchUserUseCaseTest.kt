package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
<<<<<<< HEAD
=======
import com.wire.kalium.logic.data.publicuser.ConversationMemberExcludedOptions
import com.wire.kalium.logic.data.user.UserId
>>>>>>> develop
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUsersOptions
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
<<<<<<< HEAD
import com.wire.kalium.logic.data.user.UserId
=======
>>>>>>> develop
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.publicuser.search.Result
import com.wire.kalium.logic.feature.publicuser.search.SearchUsersUseCase
import com.wire.kalium.logic.feature.publicuser.search.SearchUsersUseCaseImpl
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.Times
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
<<<<<<< HEAD
=======
import io.mockative.verify
>>>>>>> develop
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SearchUserUseCaseTest {

    @Mock
    private val searchUserRepository = mock(classOf<SearchUserRepository>())

    @Mock
    private val connectionRepository = mock(classOf<ConnectionRepository>())

    private lateinit var searchUsersUseCase: SearchUsersUseCase

    @BeforeTest
    fun setUp() {
        searchUsersUseCase = SearchUsersUseCaseImpl(searchUserRepository, connectionRepository)

        given(connectionRepository)
            .suspendFunction(connectionRepository::getConnectionRequests)
            .whenInvoked()
            .thenReturn(listOf())
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
        val actual = searchUsersUseCase(TEST_QUERY)
        // then
        assertIs<Result.Success>(actual)
        assertEquals(expected.value, actual.userSearchResult)
    }

    @Test
    fun givenPendingConnectionRequests_whenSearchingPublicUser_thenCorrectlyPropagateUserWithConnectionStatus() = runTest {
        // given
        val expected = Either.Right(VALID_SEARCH_PUBLIC_RESULT)

        given(connectionRepository)
            .suspendFunction(connectionRepository::getConnectionRequests)
            .whenInvoked()
            .thenReturn(listOf(PENDING_CONNECTION))

        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchUserDirectory)
            .whenInvokedWith(anything(), anything(), anything(), anything())
            .thenReturn(expected)
        // when
        val actual = searchUsersUseCase(TEST_QUERY)
        // then
        assertIs<Result.Success>(actual)
        assertEquals(
            actual.userSearchResult.result.first { it.id == PENDING_CONNECTION.qualifiedToId }.connectionStatus,
            ConnectionState.PENDING
        )
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
        val actual = searchUsersUseCase(TEST_QUERY_FEDERATED)
        // then
        assertIs<Result.Success>(actual)
        assertEquals(expected.value, actual.userSearchResult)
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
        val actual = searchUsersUseCase(TEST_QUERY)

        // then
        assertIs<Result.Failure.InvalidQuery>(actual)
    }

    @Test
    fun givenNoSearchOptionSpecific_whenSearchingPublicUser_thenCorrectlyPropagateDefaultSearchOption() = runTest {
        // given
        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchUserDirectory)
            .whenInvokedWith(anything(), anything(), anything(), anything())
            .thenReturn(Either.Right(VALID_SEARCH_PUBLIC_RESULT))

        // when
        searchUsersUseCase(TEST_QUERY)

        // then
        verify(searchUserRepository)
            .suspendFunction(searchUserRepository::searchUserDirectory)
            .with(anything(), anything(), anything(), eq(SearchUsersOptions.Default))
            .wasInvoked(Times(1))
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
            )
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
        searchUsersUseCase(searchQuery = TEST_QUERY, searchUsersOptions = givenSearchUsersOptions)

        // then
        verify(searchUserRepository)
            .suspendFunction(searchUserRepository::searchUserDirectory)
            .with(anything(), anything(), anything(), eq(givenSearchUsersOptions))
            .wasInvoked(Times(1))
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
                    connectionStatus = ConnectionState.ACCEPTED,
                    previewPicture = null,
                    completePicture = null,
                    availabilityStatus = UserAvailabilityStatus.NONE,
                    userType = UserType.FEDERATED
                )
            }
        )
    }

}
