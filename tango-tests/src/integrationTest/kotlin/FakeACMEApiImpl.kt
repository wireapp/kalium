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

import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.network.api.base.unbound.acme.ACMEResponse
import com.wire.kalium.network.api.base.unbound.acme.AcmeDirectoriesResponse
import com.wire.kalium.network.api.base.unbound.acme.ChallengeResponse
import com.wire.kalium.network.utils.NetworkResponse

class FakeACMEApiImpl internal constructor(
    private val unboundNetworkClient: UnboundNetworkClient
) : ACMEApi {
    private val httpClient get() = unboundNetworkClient.httpClient
    override suspend fun getACMEDirectories(): NetworkResponse<AcmeDirectoriesResponse> {

        return NetworkResponse.Success(
            value = AcmeDirectoriesResponse(
                newNonce = "nonce",
                newAccount = "newAccount",
                newOrder = "newOrder",
                revokeCert = "revokeCert",
                keyChange = "keyChange"
            ),
            headers = mapOf(),
            httpCode = 200
        )
    }

    override suspend fun getACMENonce(url: String): NetworkResponse<String> {
        return NetworkResponse.Success(
            value = "acmeNonce",
            headers = mapOf(),
            httpCode = 200
        )
    }

    override suspend fun sendACMERequest(url: String, body: ByteArray?): NetworkResponse<ACMEResponse> {
        return NetworkResponse.Success(
            value = ACMEResponse(
                nonce = "nonce",
                location = "location",
                response = byteArrayOf()
            ),
            headers = mapOf(),
            httpCode = 200
        )
    }

    override suspend fun sendChallengeRequest(url: String, body: ByteArray): NetworkResponse<ChallengeResponse> {
        return NetworkResponse.Success(
            value = ChallengeResponse(
                nonce = "nonce",
                type = "type",
                url = "url",
                status = "status",
                token = "token"
            ),
            headers = mapOf(),
            httpCode = 200
        )
    }
}
