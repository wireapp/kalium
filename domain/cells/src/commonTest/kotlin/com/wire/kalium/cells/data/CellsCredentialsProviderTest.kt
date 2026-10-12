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
package com.wire.kalium.cells.data

import com.wire.kalium.cells.CellsCredentialsProvider
import com.wire.kalium.cells.CellsCredentialsProviderImpl
import com.wire.kalium.cells.domain.model.CellsCredentials
import com.wire.kalium.cells.domain.model.WireCellsConfig
import com.wire.kalium.logic.data.featureConfig.CollaboraEdition
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CellsCredentialsProviderTest {

    @Test
    fun givenBackendUrlIsConfigured_whenRequestingCredentials_thenReturnsCredentialsWithThatUrl() = runTest {
        val provider = CellsCredentialsProviderImpl { config(TEST_BACKEND_URL) }

        assertEquals(TEST_BACKEND_URL, provider.credentials()?.serverUrl)
    }

    @Test
    fun givenBackendUrlIsMissingOrBlank_whenRequestingCredentials_thenReturnsNull() = runTest {
        assertNull(CellsCredentialsProviderImpl { null }.credentials())
        assertNull(CellsCredentialsProviderImpl { config(null) }.credentials())
        assertNull(CellsCredentialsProviderImpl { config("") }.credentials())
    }

    @Test
    fun givenBackendUrlBecomesAvailableLater_whenRequestingCredentialsAgain_thenReturnsCredentials() = runTest {
        var backendUrl: String? = null
        val provider = CellsCredentialsProviderImpl { config(backendUrl) }

        assertNull(provider.credentials())

        backendUrl = TEST_BACKEND_URL

        assertEquals(TEST_BACKEND_URL, provider.credentials()?.serverUrl)
    }

    @Test
    fun givenCredentialsWereResolved_whenRequestingCredentialsAgain_thenConfigIsNotReadAgain() = runTest {
        var configReads = 0
        val provider = CellsCredentialsProviderImpl {
            configReads++
            config(TEST_BACKEND_URL)
        }

        provider.credentials()
        provider.credentials()

        assertEquals(1, configReads)
    }

    @Test
    fun givenCredentialsArePresent_whenRequestingCredentialsOrThrow_thenReturnsCredentials() = runTest {
        val credentials = CellsCredentials(serverUrl = TEST_BACKEND_URL, gatewaySecret = "gateway-secret")

        val result = CellsCredentialsProvider { credentials }.credentialsOrThrow()

        assertEquals(credentials, result)
    }

    @Test
    fun givenCredentialsAreMissing_whenRequestingCredentialsOrThrow_thenThrowsTypedFailure() = runTest {
        val exception = assertFailsWith<CellsCredentialsUnavailableException> {
            CellsCredentialsProviderImpl { config(null) }.credentialsOrThrow()
        }

        assertEquals("Cells credentials are not available", exception.message)
    }

    private fun config(backendUrl: String?) = WireCellsConfig(
        backendUrl = backendUrl,
        collabora = CollaboraEdition.NO,
        teamQuotaBytes = null,
    )

    private companion object {
        const val TEST_BACKEND_URL = "https://drive.example.test"
    }
}
