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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.logic.data.client.E2EIClientProvider
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DebugE2EICertificateExpirationUseCaseTest {

    private val e2EIClientProvider = mock(E2EIClientProvider::class)

    @Test
    fun givenOverrideIsMissing_whenGettingExpiration_thenReturnsDefault90Days() = runTest {
        coEvery { e2EIClientProvider.getDebugCertificateExpirationOverride() }.returns(null)

        val useCase = GetDebugE2EICertificateExpirationUseCaseImpl(e2EIClientProvider)

        assertEquals(DEFAULT_E2EI_CERTIFICATE_EXPIRATION_SECONDS, useCase())
    }

    @Test
    fun givenExpirationBelowMinimum_whenSettingExpiration_thenMinimumIsStored() = runTest {
        val useCase = SetDebugE2EICertificateExpirationUseCaseImpl(e2EIClientProvider)

        useCase(120)

        coVerify { e2EIClientProvider.setDebugCertificateExpirationOverride(MIN_DEBUG_E2EI_CERTIFICATE_EXPIRATION_SECONDS) }
    }

    @Test
    fun givenExpirationAboveMinimum_whenSettingExpiration_thenProvidedValueIsStored() = runTest {
        val useCase = SetDebugE2EICertificateExpirationUseCaseImpl(e2EIClientProvider)

        useCase(900)

        coVerify { e2EIClientProvider.setDebugCertificateExpirationOverride(900) }
    }
}
