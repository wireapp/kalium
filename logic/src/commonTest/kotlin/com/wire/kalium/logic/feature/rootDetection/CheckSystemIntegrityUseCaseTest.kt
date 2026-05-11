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
package com.wire.kalium.logic.feature.rootDetection

import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sessionRepository.deleteSession(eq(Arrangement.INVALID_ACCOUNT.userId))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.sessionRepository.deleteSession(eq(Arrangement.VALID_ACCOUNT.userId))
        }
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

        verifySuspend(VerifyMode.not) {
            arrangement.sessionRepository.deleteSession(eq(Arrangement.VALID_ACCOUNT.userId))
        }
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

        verifySuspend(VerifyMode.not) {
            arrangement.sessionRepository.deleteSession(eq(Arrangement.VALID_ACCOUNT.userId))
        }
    }

    private class Arrangement {

        var kaliumConfigs = KaliumConfigs()

        val rootDetector = mock<RootDetector>(mode = MockMode.autoUnit)
        val sessionRepository = mock<SessionRepository>(mode = MockMode.autoUnit)

        fun arrange() = this to CheckSystemIntegrityUseCaseImpl(
            kaliumConfigs,
            rootDetector,
            sessionRepository
        )

        fun withKaliumConfig(kaliumConfigs: KaliumConfigs) = apply {
            this.kaliumConfigs = kaliumConfigs
        }

        fun withIsSystemRooted(result: Boolean) = apply {
            every {
                rootDetector.isSystemRooted()
            } returns result
        }

        suspend fun withAccounts(accounts: List<AccountInfo>) = apply {
            everySuspend {
                sessionRepository.allSessions()
            } returns Either.Right(accounts)
        }

        suspend fun withDeleteSessionSucceeds() = apply {
            everySuspend {
                sessionRepository.deleteSession(any())
            } returns Either.Right(Unit)
        }

        companion object {
            val VALID_ACCOUNT = AccountInfo.Valid(TestUser.OTHER_USER_ID)
            val INVALID_ACCOUNT = AccountInfo.Invalid(TestUser.OTHER_USER_ID_2, LogoutReason.SESSION_EXPIRED)
        }
    }
}
