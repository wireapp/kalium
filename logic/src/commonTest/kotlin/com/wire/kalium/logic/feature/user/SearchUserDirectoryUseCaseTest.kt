package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.model.PublicUser
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.feature.publicuser.SearchUserDirectoryUseCase
import com.wire.kalium.logic.feature.publicuser.SearchUserDirectoryUseCaseImpl
import com.wire.kalium.logic.functional.Either
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
    private val wireUserRepository = mock(classOf<SearchUserRepository>())

    private lateinit var searchUserDirectoryUseCase: SearchUserDirectoryUseCase

    @BeforeTest
    fun setUp() {
        searchUserDirectoryUseCase = SearchUserDirectoryUseCaseImpl(wireUserRepository)
    }

    @Test
    fun givenValidParams_whenSearchingPublicUser_thenCorrectlyPropagateSuccessResult() = runTest {
        //given
        val expected = Either.Right(VALID_SEARCH_PUBLIC_RESULT)

        given(wireUserRepository)
            .suspendFunction(wireUserRepository::searchUserDirectory)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(expected)
        //when
        val actual = searchUserDirectoryUseCase(TEST_QUERY, TEST_DOMAIN)
        //then
        assertIs<Either.Right<UserSearchResult>>(actual)
        assertEquals(expected, actual)
    }

    @Test
    fun givenFailure_whenSearchingPublicUser_thenCorrectlyPropagateFailureResult() = runTest {
        //given
        val expected = TEST_CORE_FAILURE

        given(wireUserRepository)
            .suspendFunction(wireUserRepository::searchUserDirectory)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(expected)
        //when
        val actual = searchUserDirectoryUseCase(TEST_QUERY, TEST_DOMAIN)

        //then
        assertIs<Either.Left<CoreFailure>>(actual)
        assertEquals(expected, actual)
    }

    private companion object {
        const val TEST_QUERY = "testQuery"
        const val TEST_DOMAIN = "testDomain"

        val TEST_CORE_FAILURE = Either.Left(CoreFailure.Unknown(IllegalStateException()))

        val VALID_SEARCH_PUBLIC_RESULT = UserSearchResult(
            result = buildList {
                for (i in 0..5) {
                    PublicUser(
                        id = UserId(i.toString(), "domain$i"),
                        name = "name$i",
                        handle = null,
                        email = null,
                        phone = null,
                        accentId = i,
                        team = null,
                        null,
                        null
                    )
                }
            }
        )
    }

}
