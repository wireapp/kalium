/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.feature.auth.ValidateUserHandleResult
import com.wire.kalium.logic.feature.auth.ValidateUserHandleUseCase
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SetUserHandleUseCaseTest {
        private val validateHandleUseCase = mock(ValidateUserHandleUseCase::class)

        private val accountRepository = mock(AccountRepository::class)

        private val syncManager = mock(SyncManager::class)

    private lateinit var setUserHandleUseCase: SetUserHandleUseCase

    @BeforeTest
    fun setup() {
        setUserHandleUseCase = SetUserHandleUseCase(accountRepository, validateHandleUseCase, syncManager)
    }

    @Test
    fun givenValidHandleAndRepositorySuccess_whenSlowSyncIsCompleted_thenLocalDataUpdatedAndSuccessIsPropagated() = runTest {
        val handle = "user_handle"
        every {
            validateHandleUseCase.invoke(any())
        }.returns(ValidateUserHandleResult.Valid(handle))
        coEvery {
            accountRepository.updateSelfHandle(handle)
        }.returns(Either.Right(Unit))
        coEvery {
            accountRepository.updateLocalSelfUserHandle(handle)
        }.returns(Either.Right(Unit))
        coEvery {
            syncManager.isSlowSyncOngoing()
        }.returns(false)
        coEvery {
            syncManager.isSlowSyncCompleted()
        }.returns(true)

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Success>(actual)

        verify {
            validateHandleUseCase.invoke(handle)
        }.wasInvoked(exactly = once)
        coVerify {
            accountRepository.updateSelfHandle(handle)
        }.wasInvoked(exactly = once)
        coVerify {
            accountRepository.updateLocalSelfUserHandle(handle)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenValidHandleAndRepositorySuccess_whenSlowSyncIsOngoing_thenLocalDataUpdatedAfterSlowSyncAndSuccessIsPropagated() = runTest {
        val handle = "user_handle"
        every {
            validateHandleUseCase.invoke(any())
        }.returns(ValidateUserHandleResult.Valid(handle))
        coEvery {
            accountRepository.updateSelfHandle(handle)
        }.returns(Either.Right(Unit))
        coEvery {
            accountRepository.updateLocalSelfUserHandle(handle)
        }.returns(Either.Right(Unit))
        coEvery {
            syncManager.isSlowSyncOngoing()
        }.returns(true)
        coEvery {
            syncManager.isSlowSyncCompleted()
        }.returns(true)

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Success>(actual)

        verify {
            validateHandleUseCase.invoke(handle)
        }.wasInvoked(exactly = once)
        coVerify {
            accountRepository.updateSelfHandle(handle)
        }.wasInvoked(exactly = once)
        coVerify {
            accountRepository.updateLocalSelfUserHandle(handle)
        }.wasInvoked(exactly = once)
        coVerify {
            syncManager.waitUntilLive()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenValidHandleAndRepositorySuccess_whenSlowSyncIsNotStarted_thenLocalDataNotUpdatedAndSuccessIsPropagated() = runTest {
        val handle = "user_handle"
        every {
            validateHandleUseCase.invoke(any())
        }.returns(ValidateUserHandleResult.Valid(handle))
        coEvery {
            accountRepository.updateSelfHandle(handle)
        }.returns(Either.Right(Unit))
        coEvery {
            syncManager.isSlowSyncOngoing()
        }.returns(false)
        coEvery {
            syncManager.isSlowSyncCompleted()
        }.returns(false)

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Success>(actual)

        verify {
            validateHandleUseCase.invoke(handle)
        }.wasInvoked(exactly = once)
        coVerify {
            accountRepository.updateSelfHandle(handle)
        }.wasInvoked(exactly = once)
        coVerify {
            accountRepository.updateLocalSelfUserHandle(handle)
        }.wasNotInvoked()
    }

    @Test
    fun givenInvalid_thenInvalidHandleErrorIsPropagated() = runTest {
        val handle = "user_handle"

        every {

            validateHandleUseCase.invoke(any())

        }.returns(ValidateUserHandleResult.Invalid.InvalidCharacters("", listOf()))
        coEvery {
            syncManager.isSlowSyncOngoing()
        }.returns(false)

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Failure.InvalidHandle>(actual)

        verify {
            validateHandleUseCase.invoke(handle)
        }.wasInvoked(exactly = once)
        coVerify {
            accountRepository.updateSelfHandle(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenValidHandleAndRepositoryFailWithGenericError_thenErrorIsPropagated() = runTest {
        val handle = "user_handle"
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
        every {
            validateHandleUseCase.invoke(any())
        }.returns(ValidateUserHandleResult.Valid(handle))
        coEvery {
            accountRepository.updateSelfHandle(handle)
        }.returns(Either.Left(expected))
        coEvery {
            syncManager.isSlowSyncOngoing()
        }.returns(false)

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Failure.Generic>(actual)
        assertIs<NetworkFailure.ServerMiscommunication>(actual.error)
        assertEquals(expected.kaliumException, (actual.error as NetworkFailure.ServerMiscommunication).kaliumException)

        verify {
            validateHandleUseCase.invoke(handle)
        }.wasInvoked(exactly = once)
        coVerify {
            accountRepository.updateSelfHandle(handle)
        }.wasInvoked(exactly = once)
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
        every {
            validateHandleUseCase.invoke(any())
        }.returns(ValidateUserHandleResult.Valid(handle))
        coEvery {
            accountRepository.updateSelfHandle(handle)
        }.returns(Either.Left(error))
        coEvery {
            syncManager.isSlowSyncOngoing()
        }.returns(false)

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Failure>(actual)
        assertEquals(expectedError, actual)

        verify {
            validateHandleUseCase.invoke(handle)
        }.wasInvoked(exactly = once)
        coVerify {
            accountRepository.updateSelfHandle(handle)
        }.wasInvoked(exactly = once)
    }

}
