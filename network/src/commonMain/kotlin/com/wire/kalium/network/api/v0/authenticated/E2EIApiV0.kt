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
package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.api.base.authenticated.e2ei.AccessTokenResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.AcmeDirectoriesResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.ACMEResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.AuthzDirectoriesResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.ChallengeResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.utils.NetworkResponse

internal open class E2EIApiV0 internal constructor() : E2EIApi {

    override suspend fun getACMEDirectories(): NetworkResponse<AcmeDirectoriesResponse> =
        E2EIApi.getApiNotSupportError(::getACMEDirectories.name)

    override suspend fun getAuhzDirectories(): NetworkResponse<AuthzDirectoriesResponse> = E2EIApi.getApiNotSupportError(::getAuhzDirectories.name)

    override suspend fun getACMENonce(url: String): NetworkResponse<String> = E2EIApi.getApiNotSupportError(::getACMENonce.name)

    override suspend fun sendACMERequest(url: String, body: ByteArray?): NetworkResponse<ACMEResponse> =
        E2EIApi.getApiNotSupportError(::sendACMERequest.name)


    override suspend fun getNewAccount(
        url: String, body: ByteArray
    ): NetworkResponse<ACMEResponse> = E2EIApi.getApiNotSupportError(::getNewAccount.name)

    override suspend fun getNewOrder(
        url: String, body: ByteArray
    ): NetworkResponse<ACMEResponse> = E2EIApi.getApiNotSupportError(::getNewOrder.name)

    override suspend fun dpopChallenge(url: String, body: ByteArray): NetworkResponse<ChallengeResponse> =
        E2EIApi.getApiNotSupportError(::dpopChallenge.name)


    override suspend fun getAuthzChallenge(
        url: String
    ): NetworkResponse<ACMEResponse> = E2EIApi.getApiNotSupportError(::getAuthzChallenge.name)

    override suspend fun getWireNonce(clientId: String): NetworkResponse<String> = E2EIApi.getApiNotSupportError(::getWireNonce.name)

    override suspend fun getAccessToken(clientId: String, dpopToken: String): NetworkResponse<AccessTokenResponse> =
        E2EIApi.getApiNotSupportError(::getAccessToken.name)

    override suspend fun oidcChallenge(url: String, body: ByteArray): NetworkResponse<ChallengeResponse> =
        E2EIApi.getApiNotSupportError(::oidcChallenge.name)
}
