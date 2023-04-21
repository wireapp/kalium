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
    suspend fun getAcmeDirectories(): NetworkResponse<AcmeDirectoriesResponse>

    suspend fun getAuhzDirectories(): NetworkResponse<AuthzDirectories>


    suspend fun getNewNonce(noncePath: String): NetworkResponse<String> // get the data from the reply header

    suspend fun postAcmeRequest(
        requestDir: String, requestBody: ByteArray
    ): NetworkResponse<AcmeResponse>

    suspend fun getNewAccount(
        newAccountRequestUrl: String, newAccountRequestBody: ByteArray
    ): NetworkResponse<AcmeResponse>

    suspend fun getNewOrder(
        url: String, body: ByteArray
    ): NetworkResponse<AcmeResponse>

    suspend fun sendNewAuthz(): NetworkResponse<Unit>

    suspend fun sendNewOrder(): NetworkResponse<Unit>

    suspend fun sendAuthzHandle(): NetworkResponse<Unit>

    suspend fun sendAuthzClienId(): NetworkResponse<Unit>

    companion object {
        fun getApiNotSupportError(apiName: String, apiVersion: String = "4") = NetworkResponse.Error(
            APINotSupported("${this::class.simpleName}: $apiName api is only available on API V$apiVersion")
        )
    }
}
