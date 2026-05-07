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
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SetUserHandleUseCaseTest {
    private val validateHandleUseCase = mock<ValidateUserHandleUseCase>()

    private val accountRepository = mock<AccountRepository>()

    private val syncManager = mock<SyncManager>()

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
        } returns ValidateUserHandleResult.Valid(handle)
        everySuspend {
            accountRepository.updateSelfHandle(handle)
        } returns Either.Right(Unit)
        everySuspend {
            accountRepository.updateLocalSelfUserHandle(handle)
        } returns Either.Right(Unit)
        everySuspend {
            syncManager.isSlowSyncOngoing()
        } returns false
        everySuspend {
            syncManager.isSlowSyncCompleted()
        } returns true

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Success>(actual)

        verify(VerifyMode.exactly(1)) {
            validateHandleUseCase.invoke(handle)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            accountRepository.updateSelfHandle(handle)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            accountRepository.updateLocalSelfUserHandle(handle)
        }
    }

    @Test
    fun givenValidHandleAndRepositorySuccess_whenSlowSyncIsOngoing_thenLocalDataUpdatedAfterSlowSyncAndSuccessIsPropagated() = runTest {
        val handle = "user_handle"
        every {
            validateHandleUseCase.invoke(any())
        } returns ValidateUserHandleResult.Valid(handle)
        everySuspend {
            accountRepository.updateSelfHandle(handle)
        } returns Either.Right(Unit)
        everySuspend {
            accountRepository.updateLocalSelfUserHandle(handle)
        } returns Either.Right(Unit)
        everySuspend {
            syncManager.isSlowSyncOngoing()
        } returns true
        everySuspend {
            syncManager.isSlowSyncCompleted()
        } returns true
        everySuspend {
            syncManager.waitUntilLive()
        } returns Unit

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Success>(actual)

        verify(VerifyMode.exactly(1)) {
            validateHandleUseCase.invoke(handle)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            accountRepository.updateSelfHandle(handle)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            accountRepository.updateLocalSelfUserHandle(handle)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            syncManager.waitUntilLive()
        }
    }

    @Test
    fun givenValidHandleAndRepositorySuccess_whenSlowSyncIsNotStarted_thenLocalDataNotUpdatedAndSuccessIsPropagated() = runTest {
        val handle = "user_handle"
        every {
            validateHandleUseCase.invoke(any())
        } returns ValidateUserHandleResult.Valid(handle)
        everySuspend {
            accountRepository.updateSelfHandle(handle)
        } returns Either.Right(Unit)
        everySuspend {
            syncManager.isSlowSyncOngoing()
        } returns false
        everySuspend {
            syncManager.isSlowSyncCompleted()
        } returns false

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Success>(actual)

        verify(VerifyMode.exactly(1)) {
            validateHandleUseCase.invoke(handle)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            accountRepository.updateSelfHandle(handle)
        }
        verifySuspend(VerifyMode.not) {
            accountRepository.updateLocalSelfUserHandle(handle)
        }
    }

    @Test
    fun givenInvalid_thenInvalidHandleErrorIsPropagated() = runTest {
        val handle = "user_handle"

        every {

            validateHandleUseCase.invoke(any())

        } returns ValidateUserHandleResult.Invalid.InvalidCharacters("", listOf())
        everySuspend {
            syncManager.isSlowSyncOngoing()
        } returns false

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Failure.InvalidHandle>(actual)

        verify(VerifyMode.exactly(1)) {
            validateHandleUseCase.invoke(handle)
        }
        verifySuspend(VerifyMode.not) {
            accountRepository.updateSelfHandle(any())
        }
    }

    @Test
    fun givenValidHandleAndRepositoryFailWithGenericError_thenErrorIsPropagated() = runTest {
        val handle = "user_handle"
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
        every {
            validateHandleUseCase.invoke(any())
        } returns ValidateUserHandleResult.Valid(handle)
        everySuspend {
            accountRepository.updateSelfHandle(handle)
        } returns Either.Left(expected)
        everySuspend {
            syncManager.isSlowSyncOngoing()
        } returns false

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Failure.Generic>(actual)
        assertIs<NetworkFailure.ServerMiscommunication>(actual.error)
        assertEquals(expected.kaliumException, (actual.error as NetworkFailure.ServerMiscommunication).kaliumException)

        verify(VerifyMode.exactly(1)) {
            validateHandleUseCase.invoke(handle)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            accountRepository.updateSelfHandle(handle)
        }
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
        } returns ValidateUserHandleResult.Valid(handle)
        everySuspend {
            accountRepository.updateSelfHandle(handle)
        } returns Either.Left(error)
        everySuspend {
            syncManager.isSlowSyncOngoing()
        } returns false

        val actual = setUserHandleUseCase(handle)

        assertIs<SetUserHandleResult.Failure>(actual)
        assertEquals(expectedError, actual)

        verify(VerifyMode.exactly(1)) {
            validateHandleUseCase.invoke(handle)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            accountRepository.updateSelfHandle(handle)
        }
    }

}
