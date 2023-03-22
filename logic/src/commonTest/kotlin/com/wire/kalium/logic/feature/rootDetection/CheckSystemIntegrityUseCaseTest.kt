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
package com.wire.kalium.logic.feature.rootDetection

import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CheckSystemIntegrityUseCaseTest {

    @Test
    fun givenDeviceIsRootedAndWipeOnRootedDeviceIsEnabled_whenInvoked_thenReturnFailureAndDeleteSessions() = runTest {
        val (arrangement, checkSystemIntegrity) = Arrangement()
            .withKaliumConfig(KaliumConfigs(wipeOnRootedDevice = true))
            .withIsSystemRooted(true)
            .withAccounts(listOf(Arrangement.INVALID_ACCOUNT, Arrangement.VALID_ACCOUNT))
            .withDeleteSessionSucceeds()
            .arrange()

        val result = checkSystemIntegrity.invoke()

        assertEquals(CheckSystemIntegrityUseCase.Result.Failed, result)

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::deleteSession)
            .with(eq(Arrangement.INVALID_ACCOUNT.userId))
            .wasInvoked(exactly = once)

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::deleteSession)
            .with(eq(Arrangement.VALID_ACCOUNT.userId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenDeviceIsNotRootedAndWipeOnRootedDeviceIsEnabled_whenInvoked_thenReturnSuccess() = runTest {
        val (arrangement, checkSystemIntegrity) = Arrangement()
            .withKaliumConfig(KaliumConfigs(wipeOnRootedDevice = true))
            .withIsSystemRooted(false)
            .withAccounts(listOf(Arrangement.VALID_ACCOUNT))
            .arrange()

        val result = checkSystemIntegrity.invoke()

        assertEquals(CheckSystemIntegrityUseCase.Result.Success, result)

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::deleteSession)
            .with(eq(Arrangement.VALID_ACCOUNT.userId))
            .wasNotInvoked()
    }

    @Test
    fun givenDeviceIsRootedAndWipeOnRootedDeviceIsDisabled_whenInvoked_thenReturnSuccess() = runTest {
        val (arrangement, checkSystemIntegrity) = Arrangement()
            .withKaliumConfig(KaliumConfigs(wipeOnRootedDevice = false))
            .withIsSystemRooted(true)
            .withAccounts(listOf(Arrangement.VALID_ACCOUNT))
            .arrange()

        val result = checkSystemIntegrity.invoke()

        assertEquals(CheckSystemIntegrityUseCase.Result.Success, result)

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::deleteSession)
            .with(eq(Arrangement.VALID_ACCOUNT.userId))
            .wasNotInvoked()
    }

    private class Arrangement {

        var kaliumConfigs = KaliumConfigs()

        @Mock
        val rootDetector = mock(classOf<RootDetector>())

        @Mock
        val sessionRepository = mock(classOf<SessionRepository>())

        fun arrange() = this to CheckSystemIntegrityUseCaseImpl(
            kaliumConfigs,
            rootDetector,
            sessionRepository
        )

        fun withKaliumConfig(kaliumConfigs: KaliumConfigs) = apply {
            this.kaliumConfigs = kaliumConfigs
        }

        fun withIsSystemRooted(result: Boolean) = apply {
            given(rootDetector)
                .function(rootDetector::isSystemRooted)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withAccounts(accounts: List<AccountInfo>) = apply {
            given(sessionRepository)
                .suspendFunction(sessionRepository::allSessions)
                .whenInvoked()
                .thenReturn(Either.Right(accounts))
        }

        fun withDeleteSessionSucceeds() = apply {
            given(sessionRepository)
                .suspendFunction(sessionRepository::deleteSession)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))
        }

        companion object {
            val VALID_ACCOUNT = AccountInfo.Valid(TestUser.OTHER_USER_ID)
            val INVALID_ACCOUNT = AccountInfo.Invalid(TestUser.OTHER_USER_ID_2, LogoutReason.SESSION_EXPIRED)
        }
    }
}
