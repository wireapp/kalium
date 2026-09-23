/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.auth.settings.PendingLoginSystemSettingsRepository
import com.wire.kalium.logic.test_util.serverMiscommunicationFailure
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.model.AuthenticationResultDTO
import com.wire.kalium.network.api.model.SessionDTO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetSSOLoginSessionUseCaseTest {
    private val repository = mock<SSOLoginRepository>(MockMode.autoUnit)
    private val login = AuthenticationResultDTO(
        SessionDTO(TestUser.SELF_USER_DTO.id, "Bearer", "fresh-token", "refresh-token", null),
        TestUser.SELF_USER_DTO,
    )
    private val settingsRepository = mock<PendingLoginSystemSettingsRepository>()
    private val useCase = GetSSOLoginSessionUseCaseImpl(repository, null, settingsRepository)

    @Test
    fun givenEmailSso_whenPreparingSession_thenSettingsAreNeverRequested() = runTest {
        everySuspend { repository.provideLoginSession("cookie") } returns Either.Right(login)

        val result = useCase("cookie")

        assertIs<SSOLoginSessionResult.Success>(result)
        verifySuspend(VerifyMode.not) { settingsRepository.isIdpChangeDetectionEnabled(any()) }
    }

    @Test
    fun givenCodeSso_whenPreparingSession_thenCapabilityUsesFreshToken() = runTest {
        for (enabled in listOf(true, false)) {
            everySuspend { repository.provideLoginSession("cookie") } returns Either.Right(login)
            everySuspend { settingsRepository.isIdpChangeDetectionEnabled(any()) } returns Either.Right(enabled)

            val result = useCase("cookie", checkIdpChangeDetection = true)

            assertIs<SSOLoginSessionResult.Success>(result)
            assertEquals(enabled, result.isIdpChangeDetectionEnabled)
            assertEquals("fresh-token", result.accountTokens.accessToken.value)
        }
    }

    @Test
    fun givenSettingsFailure_whenPreparingCodeSso_thenNoSessionIsReturned() = runTest {
        val failure = serverMiscommunicationFailure()
        everySuspend { repository.provideLoginSession("cookie") } returns Either.Right(login)
        everySuspend { settingsRepository.isIdpChangeDetectionEnabled(any()) } returns Either.Left(failure)

        val result = useCase("cookie", checkIdpChangeDetection = true)

        assertIs<SSOLoginSessionResult.Failure.Generic>(result)
        assertEquals(failure, result.genericFailure)
    }
}
