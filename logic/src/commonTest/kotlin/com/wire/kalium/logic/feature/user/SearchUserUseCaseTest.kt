package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.publicuser.search.Result
import com.wire.kalium.logic.feature.publicuser.search.SearchUsersUseCase
import com.wire.kalium.logic.feature.publicuser.search.SearchUsersUseCaseImpl
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SearchUserUseCaseTest {

    @Mock
    private val searchUserRepository = mock(classOf<SearchUserRepository>())

    @Mock
    private val userRepository = mock(classOf<UserRepository>())

    private lateinit var searchUsersUseCase: SearchUsersUseCase

    @BeforeTest
    fun setUp() {
        searchUsersUseCase = SearchUsersUseCaseImpl(userRepository, searchUserRepository)

        given(userRepository)
            .suspendFunction(userRepository::observeSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))
    }

    @Test
    fun givenValidParams_whenSearchingPublicUser_thenCorrectlyPropagateSuccessResult() = runTest {
        //given
        val expected = Either.Right(VALID_SEARCH_PUBLIC_RESULT)

        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchUserDirectory)
            .whenInvokedWith(anything(), anything(), anything(), anything())
            .thenReturn(expected)
        //when
        val actual = searchUsersUseCase(TEST_QUERY)
        //then
        assertIs<Result.Success>(actual)
        assertEquals(expected.value, actual.userSearchResult)
    }

    @Test
    fun givenValidParams_federated_whenSearchingPublicUser_thenCorrectlyPropagateSuccessResult() = runTest {
        //given
        val expected = Either.Right(VALID_SEARCH_PUBLIC_RESULT)

        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchUserDirectory)
            .whenInvokedWith(eq("testQuery"), eq("wire.com"), anything(), anything())
            .thenReturn(expected)
        //when
        val actual = searchUsersUseCase(TEST_QUERY_FEDERATED)
        //then
        assertIs<Result.Success>(actual)
        assertEquals(expected.value, actual.userSearchResult)
    }

    @Test
    fun givenFailure_whenSearchingPublicUser_thenCorrectlyPropagateFailureResult() = runTest {
        //given
        val expected = TEST_CORE_FAILURE

        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchUserDirectory)
            .whenInvokedWith(eq("testQuery"), eq("domain"), anything(), anything())
            .thenReturn(expected)
        //when
        val actual = searchUsersUseCase(TEST_QUERY)

        //then
        assertIs<Result.Failure.InvalidQuery>(actual)
    }

    private companion object {
        const val TEST_QUERY = "testQuery"
        const val TEST_QUERY_FEDERATED = "testQuery@wire.com"

        val TEST_CORE_FAILURE = Either.Left(
            NetworkFailure.ServerMiscommunication(KaliumException.InvalidRequestError(ErrorResponse(404, "a", "")))
        )

        val VALID_SEARCH_PUBLIC_RESULT = UserSearchResult(
            result = buildList {
                for (i in 0..5) {
                    OtherUser(
                        id = UserId(i.toString(), "domain$i"),
                        name = "name$i",
                        handle = null,
                        email = null,
                        phone = null,
                        accentId = i,
                        team = null,
                        connectionStatus = ConnectionState.ACCEPTED,
                        previewPicture = null,
                        completePicture = null,
                        availabilityStatus = UserAvailabilityStatus.NONE,
                        userType = UserType.FEDERATED
                    )
                }
            }
        )
    }

}
