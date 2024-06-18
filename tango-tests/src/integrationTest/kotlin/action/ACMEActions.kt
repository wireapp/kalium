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
package action

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.mocks.requests.ACMERequests.ACME_BASE_URL
import com.wire.kalium.mocks.responses.ACMEApiResponseJsonSample.ACME_DIRECTORIES_SAMPLE
import com.wire.kalium.network.api.base.unbound.acme.AcmeDirectoriesResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import kotlin.test.assertEquals
import kotlin.test.assertIs

object ACMEActions {

    suspend fun acmeDirectoriesErrorNotConnected(coreLogic: CoreLogic) = coreLogic.getGlobalScope()
        .unboundNetworkContainer
        .acmeApi.getACMEDirectories(ACME_BASE_URL).also { actual ->
            assertIs<NetworkResponse.Error>(actual)
            assertIs<KaliumException.NoNetwork>(actual.kException.cause)
        }

    suspend fun acmeDirectoriesSuccess(coreLogic: CoreLogic, expected: AcmeDirectoriesResponse = ACME_DIRECTORIES_SAMPLE) =
        coreLogic.getGlobalScope()
            .unboundNetworkContainer
            .acmeApi.getACMEDirectories(ACME_BASE_URL).also { actual ->
                assertIs<NetworkResponse.Success<AcmeDirectoriesResponse>>(actual)
                assertEquals(expected, actual.value)
            }

    suspend fun acmeDirectoriesConnectNoInternet(coreLogic: CoreLogic) = coreLogic.getGlobalScope().unboundNetworkContainer
        .acmeApi.getACMEDirectories(ACME_BASE_URL).also { actual ->
            assertIs<NetworkResponse.Error>(actual)
            assertIs<KaliumException.NoNetwork>(actual.kException.cause)
        }
}
