package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SetUserHandleUseCaseTest {
    @Mock
    private val validateHandleUseCase = mock(classOf<ValidateUserHandleUseCase>())

    @Mock
    private val userRepository = mock(classOf<UserRepository>())

    private lateinit var setUserHandleUseCase: SetUserHandleUseCase

    @BeforeTest
    fun setup() {
        setUserHandleUseCase = SetUserHandleUseCase(userRepository, validateHandleUseCase)
    }

    @Test
    fun givenValidHandleAndRepositorySuccess_thenSuccessIsPropagated() = runTest {
        val handle = "user_handle"
        given(validateHandleUseCase)
            .function(validateHandleUseCase::invoke)
            .whenInvokedWith(any())
            .then { true }
        given(userRepository)
            .coroutine { updateSelfHandle(handle) }
            .then { Either.Right(Unit) }

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Success>(actual)

        verify(validateHandleUseCase)
            .invocation { invoke(handle) }
            .wasInvoked(exactly = once)
        verify(userRepository)
            .coroutine { updateSelfHandle(handle) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenInvalid_thenInvalidHandleErrorIsPropagated() = runTest {
        val handle = "user_handle"

        given(validateHandleUseCase)
            .function(validateHandleUseCase::invoke)
            .whenInvokedWith(any())
            .then { false }

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Failure.InvalidHandle>(actual)

        verify(validateHandleUseCase)
            .invocation { invoke(handle) }
            .wasInvoked(exactly = once)
        verify(userRepository)
            .suspendFunction(userRepository::updateSelfHandle)
            .with(any())
            .wasNotInvoked()
    }


    @Test
    fun givenValidHandleAndRepositoryFailWithGenericError_thenErrorIsPropagated() = runTest {
        val handle = "user_handle"
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
        given(validateHandleUseCase)
            .function(validateHandleUseCase::invoke)
            .whenInvokedWith(any())
            .then { true }
        given(userRepository)
            .coroutine { updateSelfHandle(handle) }
            .then { Either.Left(expected) }

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Failure.Generic>(actual)
        assertEquals(expected, actual.error)

        verify(validateHandleUseCase)
            .invocation { invoke(handle) }
            .wasInvoked(exactly = once)
        verify(userRepository)
            .coroutine { updateSelfHandle(handle) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenValidHandleAndRepositoryFailWithInvalidHandle_thenInvalidHandleIsPropagated() = testErrors(TestNetworkException.invalidHandle, SetUserHandleResult.Failure.InvalidHandle)

    @Test
    fun givenValidHandleAndRepositoryFailWithHandleExists_thenHandleExistsPropagated() = testErrors(TestNetworkException.handleExists, SetUserHandleResult.Failure.HandleExists)


    private fun testErrors(kaliumException: KaliumException, expectedError: SetUserHandleResult.Failure) = runTest {
        val handle = "user_handle"
        val error = NetworkFailure.ServerMiscommunication(kaliumException)
        given(validateHandleUseCase)
            .function(validateHandleUseCase::invoke)
            .whenInvokedWith(any())
            .then { true }
        given(userRepository)
            .coroutine { updateSelfHandle(handle) }
            .then { Either.Left(error) }

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Failure>(actual)
        assertEquals(expectedError, actual)

        verify(validateHandleUseCase)
            .invocation { invoke(handle) }
            .wasInvoked(exactly = once)
        verify(userRepository)
            .coroutine { updateSelfHandle(handle) }
            .wasInvoked(exactly = once)
    }

}
