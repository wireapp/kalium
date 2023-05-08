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
package com.wire.kalium.network.api.base.authenticated.e2ei

import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.utils.NetworkResponse

interface E2EIApi {
    suspend fun getACMEDirectories(): NetworkResponse<AcmeDirectoriesResponse>

    suspend fun getACMENonce(url: String): NetworkResponse<String>

    suspend fun sendACMERequest(url: String, body: ByteArray? = null): NetworkResponse<ACMEResponse>

    suspend fun getNewAccount(url: String, body: ByteArray): NetworkResponse<ACMEResponse>

    suspend fun getNewOrder(url: String, body: ByteArray): NetworkResponse<ACMEResponse>

    suspend fun dpopChallenge(url: String, body: ByteArray): NetworkResponse<ChallengeResponse>

    suspend fun oidcChallenge(url: String, body: ByteArray): NetworkResponse<ChallengeResponse>

    suspend fun getAuthzChallenge(url: String): NetworkResponse<ACMEResponse>

    suspend fun getAuhzDirectories(): NetworkResponse<AuthzDirectoriesResponse>

    suspend fun getAccessToken(clientId: String, dpopToken: String): NetworkResponse<AccessTokenResponse>

    suspend fun getWireNonce(clientId: String): NetworkResponse<String>

    companion object {
        fun getApiNotSupportError(apiName: String, apiVersion: String = "4") = NetworkResponse.Error(
            APINotSupported("${this::class.simpleName}: $apiName api is only available on API V$apiVersion")
        )
    }
}
