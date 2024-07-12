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

package com.wire.kalium.api.v5

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.model.SupportedProtocolDTO
import com.wire.kalium.network.api.v5.authenticated.SelfApiV5
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
internal class SelfApiV5Test : ApiTest() {
    @Test
    fun givenValidRequest_whenUpdatingSupportedProtocols_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                "",
                statusCode = HttpStatusCode.OK,
                assertion = {
                    assertPut()
                    assertNoQueryParams()
                    assertPathEqual("$PATH_SELF/$PATH_SUPPORTED_PROTOCOLS")
                }
            )
            val selfApi = SelfApiV5(networkClient, TEST_SESSION_MANAGER)
            val response = selfApi.updateSupportedProtocols(listOf(SupportedProtocolDTO.MLS))
            assertTrue(response.isSuccessful())
        }

    private companion object {
        const val PATH_SELF = "/self"
        const val PATH_SUPPORTED_PROTOCOLS = "supported-protocols"
    }
}
