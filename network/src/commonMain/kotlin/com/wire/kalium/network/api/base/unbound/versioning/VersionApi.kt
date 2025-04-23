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

package com.wire.kalium.network.api.base.unbound.versioning

import com.wire.kalium.network.BackendMetaDataUtil
import com.wire.kalium.network.BackendMetaDataUtilImpl
import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.api.unbound.versioning.VersionInfoDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.setUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.mockative.Mockable

@Mockable
interface VersionApi {
    suspend fun fetchApiVersion(baseApiUrl: Url): NetworkResponse<ServerConfigDTO.MetaData>
}

class VersionApiImpl internal constructor(
    private val httpClient: HttpClient,
    private val developmentApiEnabled: Boolean,
    private val util: BackendMetaDataUtil = BackendMetaDataUtilImpl
) : VersionApi {

    internal constructor(unauthenticatedNetworkClient: UnauthenticatedNetworkClient, developmentApiEnabled: Boolean) : this(
        unauthenticatedNetworkClient.httpClient,
        developmentApiEnabled = developmentApiEnabled
    )

    override suspend fun fetchApiVersion(baseApiUrl: Url): NetworkResponse<ServerConfigDTO.MetaData> = wrapKaliumResponse({
        if (it.status.value != HttpStatusCode.NotFound.value) null
        else {
            NetworkResponse.Success(VersionInfoDTO(), it)
        }
    }, {
        httpClient.get {
            setUrl(baseApiUrl, API_VERSION_PATH)
        }
    }).mapSuccess {
        util.calculateApiVersion(it, developmentApiEnabled = developmentApiEnabled)
    }

    private companion object {
        const val API_VERSION_PATH = "api-version"
    }

}
