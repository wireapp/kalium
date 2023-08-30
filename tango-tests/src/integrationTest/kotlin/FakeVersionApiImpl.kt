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

import com.wire.kalium.network.BackendMetaDataUtil
import com.wire.kalium.network.BackendMetaDataUtilImpl
import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.client.*
import io.ktor.http.Url

class FakeVersionApiImpl internal constructor(
    private val httpClient: HttpClient,
    private val util: BackendMetaDataUtil = BackendMetaDataUtilImpl,
    private val developmentApiEnabled: Boolean
) : VersionApi {
    internal constructor(unboundNetworkClient: UnboundNetworkClient, developmentApiEnabled: Boolean) : this(
        unboundNetworkClient.httpClient,
        developmentApiEnabled = developmentApiEnabled
    )

    override suspend fun fetchApiVersion(baseApiUrl: Url): NetworkResponse<ServerConfigDTO.MetaData> {
        return NetworkResponse.Success(
            value = ServerConfigDTO.MetaData(
                federation = false,
                commonApiVersion = ApiVersionDTO.Valid(ApiVersionDTO.MINIMUM_VALID_API_VERSION),
                domain = "wire.com"
            ),
            headers = mapOf(),
            httpCode = 200
        )
    }

    private companion object {
        const val API_VERSION_PATH = "api-version"
    }

}
