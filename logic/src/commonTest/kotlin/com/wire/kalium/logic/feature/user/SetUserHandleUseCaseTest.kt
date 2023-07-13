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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.feature.auth.ValidateUserHandleResult
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
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
    private val accountRepository = configure(mock(classOf<AccountRepository>())) { stubsUnitByDefault = true }

    @Mock
    private val syncManager = configure(mock(classOf<SyncManager>())) { stubsUnitByDefault = true }

    private lateinit var setUserHandleUseCase: SetUserHandleUseCase

    @BeforeTest
    fun setup() {
        setUserHandleUseCase = SetUserHandleUseCase(accountRepository, validateHandleUseCase, syncManager)
    }

    @Test
    fun givenValidHandleAndRepositorySuccess_whenSlowSyncIsCompleted_thenLocalDataUpdatedAndSuccessIsPropagated() = runTest {
        val handle = "user_handle"
        given(validateHandleUseCase)
            .function(validateHandleUseCase::invoke)
            .whenInvokedWith(any())
            .then { ValidateUserHandleResult.Valid(handle) }
        given(accountRepository)
            .coroutine { updateSelfHandle(handle) }
            .then { Either.Right(Unit) }
        given(accountRepository)
            .coroutine { updateLocalSelfUserHandle(handle) }
            .then { Either.Right(Unit) }
        given(syncManager)
            .coroutine { syncManager.isSlowSyncOngoing() }
            .then { false }
        given(syncManager)
            .coroutine { syncManager.isSlowSyncCompleted() }
            .then { true }

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Success>(actual)

        verify(validateHandleUseCase)
            .invocation { invoke(handle) }
            .wasInvoked(exactly = once)
        verify(accountRepository)
            .coroutine { updateSelfHandle(handle) }
            .wasInvoked(exactly = once)
        verify(accountRepository)
            .coroutine { updateLocalSelfUserHandle(handle) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenValidHandleAndRepositorySuccess_whenSlowSyncIsOngoing_thenLocalDataUpdatedAfterSlowSyncAndSuccessIsPropagated() = runTest {
        val handle = "user_handle"
        given(validateHandleUseCase)
            .function(validateHandleUseCase::invoke)
            .whenInvokedWith(any())
            .then { ValidateUserHandleResult.Valid(handle) }
        given(accountRepository)
            .coroutine { updateSelfHandle(handle) }
            .then { Either.Right(Unit) }
        given(accountRepository)
            .coroutine { updateLocalSelfUserHandle(handle) }
            .then { Either.Right(Unit) }
        given(syncManager)
            .coroutine { syncManager.isSlowSyncOngoing() }
            .then { true }
        given(syncManager)
            .coroutine { syncManager.isSlowSyncCompleted() }
            .then { true }

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Success>(actual)

        verify(validateHandleUseCase)
            .invocation { invoke(handle) }
            .wasInvoked(exactly = once)
        verify(accountRepository)
            .coroutine { updateSelfHandle(handle) }
            .wasInvoked(exactly = once)
        verify(accountRepository)
            .coroutine { updateLocalSelfUserHandle(handle) }
            .wasInvoked(exactly = once)
        verify(syncManager)
            .coroutine { waitUntilLive() }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenValidHandleAndRepositorySuccess_whenSlowSyncIsNotStarted_thenLocalDataNotUpdatedAndSuccessIsPropagated() = runTest {
        val handle = "user_handle"
        given(validateHandleUseCase)
            .function(validateHandleUseCase::invoke)
            .whenInvokedWith(any())
            .then { ValidateUserHandleResult.Valid(handle) }
        given(accountRepository)
            .coroutine { updateSelfHandle(handle) }
            .then { Either.Right(Unit) }
        given(syncManager)
            .coroutine { syncManager.isSlowSyncOngoing() }
            .then { false }
        given(syncManager)
            .coroutine { syncManager.isSlowSyncCompleted() }
            .then { false }

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Success>(actual)

        verify(validateHandleUseCase)
            .invocation { invoke(handle) }
            .wasInvoked(exactly = once)
        verify(accountRepository)
            .coroutine { updateSelfHandle(handle) }
            .wasInvoked(exactly = once)
        verify(accountRepository)
            .coroutine { updateLocalSelfUserHandle(handle) }
            .wasNotInvoked()
    }

    @Test
    fun givenInvalid_thenInvalidHandleErrorIsPropagated() = runTest {
        val handle = "user_handle"

        given(validateHandleUseCase)
            .function(validateHandleUseCase::invoke)
            .whenInvokedWith(any())
            .then { ValidateUserHandleResult.Invalid.InvalidCharacters("", listOf()) }
        given(syncManager)
            .coroutine { syncManager.isSlowSyncOngoing() }
            .then { false }

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Failure.InvalidHandle>(actual)

        verify(validateHandleUseCase)
            .invocation { invoke(handle) }
            .wasInvoked(exactly = once)
        verify(accountRepository)
            .suspendFunction(accountRepository::updateSelfHandle)
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
            .then { ValidateUserHandleResult.Valid(handle) }
        given(accountRepository)
            .coroutine { updateSelfHandle(handle) }
            .then { Either.Left(expected) }
        given(syncManager)
            .coroutine { syncManager.isSlowSyncOngoing() }
            .then { false }

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Failure.Generic>(actual)
        assertIs<NetworkFailure.ServerMiscommunication>(actual.error)
        assertEquals(expected.kaliumException, (actual.error as NetworkFailure.ServerMiscommunication).kaliumException)

        verify(validateHandleUseCase)
            .invocation { invoke(handle) }
            .wasInvoked(exactly = once)
        verify(accountRepository)
            .coroutine { updateSelfHandle(handle) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenValidHandleAndRepositoryFailWithInvalidHandle_thenInvalidHandleIsPropagated() =
        testErrors(TestNetworkException.invalidHandle, SetUserHandleResult.Failure.InvalidHandle)

    @Test
    fun givenValidHandleAndRepositoryFailWithHandleExists_thenHandleExistsPropagated() =
        testErrors(TestNetworkException.handleExists, SetUserHandleResult.Failure.HandleExists)

    private fun testErrors(kaliumException: KaliumException, expectedError: SetUserHandleResult.Failure) = runTest {
        val handle = "user_handle"
        val error = NetworkFailure.ServerMiscommunication(kaliumException)
        given(validateHandleUseCase)
            .function(validateHandleUseCase::invoke)
            .whenInvokedWith(any())
            .then { ValidateUserHandleResult.Valid(handle) }
        given(accountRepository)
            .coroutine { updateSelfHandle(handle) }
            .then { Either.Left(error) }
        given(syncManager)
            .coroutine { syncManager.isSlowSyncOngoing() }
            .then { false }

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Failure>(actual)
        assertEquals(expectedError, actual)

        verify(validateHandleUseCase)
            .invocation { invoke(handle) }
            .wasInvoked(exactly = once)
        verify(accountRepository)
            .coroutine { updateSelfHandle(handle) }
            .wasInvoked(exactly = once)
    }

}
