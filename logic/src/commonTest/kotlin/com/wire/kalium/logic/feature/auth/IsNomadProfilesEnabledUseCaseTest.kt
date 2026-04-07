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

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.auth.settings.UnauthorizedSettingsRepository
import com.wire.kalium.logic.test_util.TestNetworkException
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class IsNomadProfilesEnabledUseCaseTest {

    private val unauthorizedSettingsRepository = mock<UnauthorizedSettingsRepository>()
    private lateinit var useCase: IsNomadProfilesEnabledUseCase

    @BeforeTest
    fun setUp() {
        useCase = IsNomadProfilesEnabledUseCase(unauthorizedSettingsRepository)
    }

    @Test
    fun givenRepositoryReturnsSuccess_whenInvoked_thenReturnsEnabledState() = runTest {
        everySuspend { unauthorizedSettingsRepository.isNomadProfilesEnabled() } returns Either.Right(true)

        val result = useCase()

        assertEquals(IsNomadProfilesEnabledUseCase.Result.Success(true), result)
    }

    @Test
    fun givenRepositoryReturnsFailure_whenInvoked_thenReturnsFailureResult() = runTest {
        val expectedFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
        everySuspend { unauthorizedSettingsRepository.isNomadProfilesEnabled() } returns Either.Left(expectedFailure)

        val result = useCase()

        assertIs<IsNomadProfilesEnabledUseCase.Result.Failure>(result)
        assertEquals(expectedFailure, result.error)
    }
}
