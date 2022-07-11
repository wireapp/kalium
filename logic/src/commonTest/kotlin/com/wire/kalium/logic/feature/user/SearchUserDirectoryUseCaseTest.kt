package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.publicuser.search.Result
import com.wire.kalium.logic.feature.publicuser.search.SearchUserDirectoryUseCase
import com.wire.kalium.logic.feature.publicuser.search.SearchUserDirectoryUseCaseImpl
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SearchUserDirectoryUseCaseTest {

    @Mock
    private val searchUserRepository = mock(classOf<SearchUserRepository>())

    private lateinit var searchUserDirectoryUseCase: SearchUserDirectoryUseCase

    @BeforeTest
    fun setUp() {
        searchUserDirectoryUseCase = SearchUserDirectoryUseCaseImpl(searchUserRepository)
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
        val actual = searchUserDirectoryUseCase(TEST_QUERY, TEST_DOMAIN)
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
            .whenInvokedWith(anything(), anything(), anything(), anything())
            .thenReturn(expected)
        //when
        val actual = searchUserDirectoryUseCase(TEST_QUERY, TEST_DOMAIN)

        //then
        assertIs<Result.Failure.InvalidQuery>(actual)
    }

    private companion object {
        const val TEST_QUERY = "testQuery"
        const val TEST_DOMAIN = "testDomain"

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
                        teamId = null,
                        connectionStatus = ConnectionState.ACCEPTED,
                        previewPicture = null,
                        completePicture = null,
                        availabilityStatus = UserAvailabilityStatus.NONE,
                        userType =  UserType.EXTERNAL
                    )
                }
            }
        )
    }

}
