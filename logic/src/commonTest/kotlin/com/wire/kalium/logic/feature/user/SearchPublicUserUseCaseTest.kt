package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.publicuser.PublicUserRepository
import com.wire.kalium.logic.data.publicuser.model.PublicUser
import com.wire.kalium.logic.data.publicuser.model.PublicUserSearchResult
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.user.search.SearchPublicUserUseCase
import com.wire.kalium.logic.feature.user.search.SearchPublicUserUseCaseImpl
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

class SearchPublicUserUseCaseTest {

    @Mock
    private val publicUserRepository = mock(classOf<PublicUserRepository>())

    private lateinit var searchPublicUserUseCase: SearchPublicUserUseCase

    @BeforeTest
    fun setUp() {
        searchPublicUserUseCase = SearchPublicUserUseCaseImpl(publicUserRepository)
    }

    @Test
    fun givenValidParams_whenSearchingPublicUser_thenCorrectlyPropagateSuccessResult() = runTest {
        //given
        val expected = Either.Right(VALID_SEARCH_PUBLIC_RESULT)

        given(publicUserRepository)
            .suspendFunction(publicUserRepository::searchPublicContact)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(expected)
        //when
        val actual = searchPublicUserUseCase(TEST_QUERY, TEST_DOMAIN)
        //then
        assertIs<Either.Right<PublicUserSearchResult>>(actual)
        assertEquals(expected, actual)
    }

    @Test
    fun givenFailure_whenSearchingPublicUser_thenCorrectlyPropagateFailureResult() = runTest {
        //given
        val expected = TEST_CORE_FAILURE

        given(publicUserRepository)
            .suspendFunction(publicUserRepository::searchPublicContact)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(expected)
        //when
        val actual = searchPublicUserUseCase(TEST_QUERY, TEST_DOMAIN)

        //then
        assertIs<Either.Left<CoreFailure>>(actual)
        assertEquals(expected, actual)
    }

    private companion object {
        const val TEST_QUERY = "testQuery"
        const val TEST_DOMAIN = "testDomain"

        val TEST_CORE_FAILURE = Either.Left(CoreFailure.Unknown(IllegalStateException()))

        val VALID_SEARCH_PUBLIC_RESULT = PublicUserSearchResult(
            totalFound = 5,
            publicUsers = buildList {
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
