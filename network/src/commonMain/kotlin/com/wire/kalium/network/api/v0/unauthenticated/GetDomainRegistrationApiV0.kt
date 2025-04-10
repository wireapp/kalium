/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.network.api.v0.unauthenticated

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.base.unauthenticated.domainregistration.GetDomainRegistrationApi
import com.wire.kalium.network.api.base.unauthenticated.domainregistration.GetDomainRegistrationApi.Companion.MIN_API_VERSION
import com.wire.kalium.network.api.unauthenticated.domainLookup.DomainLookupResponse
import com.wire.kalium.network.api.unauthenticated.domainregistration.DomainRegistrationDTO
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.utils.NetworkResponse

internal open class GetDomainRegistrationApiV0 internal constructor(
    private val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : GetDomainRegistrationApi {

    internal val httpClient get() = unauthenticatedNetworkClient.httpClient

    override suspend fun getDomainRegistration(email: String): NetworkResponse<DomainRegistrationDTO> {
        return NetworkResponse.Error(
            APINotSupported(
                errorBody = "${this::class.simpleName}: ${::getDomainRegistration.name} api is only available on API V${MIN_API_VERSION}"
            )
        )
    }

    override suspend fun customBackendConfig(backendUrl: String): NetworkResponse<DomainLookupResponse> {
        return NetworkResponse.Error(
            APINotSupported(
                errorBody = "${this::class.simpleName}: ${::customBackendConfig.name} api is only available on API V${MIN_API_VERSION}"
            )
        )
    }

}
