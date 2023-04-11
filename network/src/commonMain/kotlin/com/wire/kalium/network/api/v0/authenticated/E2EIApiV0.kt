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

import com.wire.kalium.network.api.base.authenticated.e2ei.AcmeDirectoriesResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.utils.NetworkResponse

internal open class E2EIApiV0 internal constructor() : E2EIApi {

    override suspend fun getDirectories(): NetworkResponse<ByteArray> =
        E2EIApi.getApiNotSupportError(::getDirectories.name)

    override suspend fun getNewNonce(noncePath: String): NetworkResponse<String> =
        E2EIApi.getApiNotSupportError(::getNewNonce.name)

    override suspend fun sendNewAccount(
        newAccountRequest: String,
        newAccountRequestBody: List<UByte>
    ): NetworkResponse<String> =
        E2EIApi.getApiNotSupportError(::sendNewAccount.name)

    override suspend fun sendNewAuthz(): NetworkResponse<Unit> =
        E2EIApi.getApiNotSupportError(::sendNewAuthz.name)

    override suspend fun sendNewOrder(): NetworkResponse<Unit> =
        E2EIApi.getApiNotSupportError(::sendNewOrder.name)

    override suspend fun sendAuthzHandle(): NetworkResponse<Unit> =
        E2EIApi.getApiNotSupportError(::sendAuthzHandle.name)

    override suspend fun sendAuthzClienId(): NetworkResponse<Unit> =
        E2EIApi.getApiNotSupportError(::sendAuthzClienId.name)
}
