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
package com.wire.kalium.network.api.v10.unauthenticated

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.model.ApiModelMapper
import com.wire.kalium.network.api.model.ApiModelMapperImpl
import com.wire.kalium.network.api.unauthenticated.domainregistration.DomainRegistrationDTO
import com.wire.kalium.network.api.unauthenticated.domainregistration.DomainRegistrationDTOV10
import com.wire.kalium.network.api.unauthenticated.domainregistration.DomainRegistrationRequest
import com.wire.kalium.network.api.v9.unauthenticated.GetDomainRegistrationApiV9
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal open class GetDomainRegistrationApiV10 internal constructor(
    unauthenticatedNetworkClient: UnauthenticatedNetworkClient,
    private val apiModelMapper: ApiModelMapper = ApiModelMapperImpl(),
) : GetDomainRegistrationApiV9(unauthenticatedNetworkClient) {

    override suspend fun getDomainRegistration(email: String): NetworkResponse<DomainRegistrationDTO> =
        wrapKaliumResponse<DomainRegistrationDTOV10> {
            httpClient.post(PATH_DOMAIN_REGISTRATION) {
                setBody(DomainRegistrationRequest(email))
            }
        }.mapSuccess { domainRegistrationV10 ->
            apiModelMapper.fromApiV10(domainRegistrationV10)
        }
}
