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

package com.wire.kalium.logic.data.auth.settings

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.base.unauthenticated.systemsettings.UnauthorizedSettingsApi
import com.wire.kalium.network.api.unauthenticated.systemsettings.UnauthorizedSettingsResponse
import com.wire.kalium.network.utils.NetworkResponse
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class UnauthorizedSettingsRepositoryTest {

    private val unauthorizedSettingsApi = mock<UnauthorizedSettingsApi>()
    private lateinit var repository: UnauthorizedSettingsRepository

    @BeforeTest
    fun setup() {
        repository = UnauthorizedSettingsRepositoryImpl(unauthorizedSettingsApi)
    }

    @Test
    fun givenNomadProfilesIsTrue_whenRequestingFlag_thenReturnsTrue() = runTest {
        everySuspend { unauthorizedSettingsApi.settings() } returns
            NetworkResponse.Success(UnauthorizedSettingsResponse(false, true), mapOf(), 200)

        val result = repository.isNomadProfilesEnabled()

        assertIs<Either.Right<Boolean>>(result)
        assertTrue(result.value)
        verifySuspend(VerifyMode.exactly(1)) { unauthorizedSettingsApi.settings() }
    }

    @Test
    fun givenNomadProfilesIsMissing_whenRequestingFlag_thenReturnsFalse() = runTest {
        everySuspend { unauthorizedSettingsApi.settings() } returns
            NetworkResponse.Success(UnauthorizedSettingsResponse(false, null), mapOf(), 200)

        val result = repository.isNomadProfilesEnabled()

        assertIs<Either.Right<Boolean>>(result)
        assertFalse(result.value)
        verifySuspend(VerifyMode.exactly(1)) { unauthorizedSettingsApi.settings() }
    }

    @Test
    fun givenRequestFails_whenRequestingFlag_thenReturnsFailure() = runTest {
        val expectedException = TestNetworkException.generic
        everySuspend { unauthorizedSettingsApi.settings() } returns NetworkResponse.Error(expectedException)

        val result = repository.isNomadProfilesEnabled()

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result)
        assertEquals(expectedException, result.value.kaliumException)
        verifySuspend(VerifyMode.exactly(1)) { unauthorizedSettingsApi.settings() }
    }
}
