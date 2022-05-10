package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestUser.OTHER
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetUserInfoUseCaseTest {

    @Mock
    private val userRepository: UserRepository = mock(UserRepository::class)

    lateinit var getUserInfoUseCase: GetUserInfoUseCase

    @BeforeTest
    fun setUp() {
        getUserInfoUseCase = GetUserInfoUseCaseImpl(userRepository)
    }

    @Test
    fun givenAUserId_whenInvokingGetUserInfoDetails_thenShouldReturnsASuccessResult() = runTest {
        // given
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(eq(userId))
            .thenReturn(flowOf(null))

        given(userRepository)
            .suspendFunction(userRepository::fetchUserInfo)
            .whenInvokedWith(eq(userId))
            .thenReturn(Either.Right(OTHER))

        // when
        val result = getUserInfoUseCase(userId)

        // then
        assertEquals(OTHER, (result as GetUserInfoResult.Success).otherUser)
        verify(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .with(eq(userId))
            .wasInvoked(once)

        verify(userRepository)
            .suspendFunction(userRepository::fetchUserInfo)
            .with(eq(userId))
            .wasInvoked(once)
    }


    @Test
    fun givenAUserId_whenInvokingGetUserInfoDetailsAndExistsLocally_thenShouldReturnsImmediatelySuccessResult() = runTest {
        // given
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(eq(userId))
            .thenReturn(flowOf(OTHER))

        // when
        val result = getUserInfoUseCase(userId)

        // then
        assertEquals(OTHER, (result as GetUserInfoResult.Success).otherUser)
        verify(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .with(eq(userId))
            .wasInvoked(once)

        verify(userRepository)
            .suspendFunction(userRepository::fetchUserInfo)
            .with(eq(userId))
            .wasNotInvoked()
    }


    @Test
    fun givenAUserId_whenInvokingGetUserInfoDetailsWithErrors_thenShouldReturnsAFailure() = runTest {
        // given
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(eq(userId))
            .thenReturn(flowOf(null))

        given(userRepository)
            .suspendFunction(userRepository::fetchUserInfo)
            .whenInvokedWith(eq(userId))
            .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))

        // when
        val result = getUserInfoUseCase(userId)

        // then
        assertEquals(GetUserInfoResult.Failure, result)
        verify(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .with(eq(userId))
            .wasInvoked(once)

        verify(userRepository)
            .suspendFunction(userRepository::fetchUserInfo)
            .with(eq(userId))
            .wasInvoked(once)
    }

    private companion object {
        val userId = UserId("some_user", "some_domain")
    }

}

