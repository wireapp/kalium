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
package com.wire.kalium.network.api.v0.unauthenticated

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.api.base.unauthenticated.domainLookup.DomainLookupApi
import com.wire.kalium.network.api.unauthenticated.domainLookup.DomainLookupResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get

internal open class DomainLookupApiV0 internal constructor(
    private val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : DomainLookupApi {
    private val httpClient get() = unauthenticatedNetworkClient.httpClient

    override suspend fun lookup(domain: String): NetworkResponse<DomainLookupResponse> = wrapKaliumResponse {
        httpClient.get("$CUSTOM_BACKEND_PATH/$BY_DOMAIN_PATH/$domain")
    }

    private companion object {
        const val CUSTOM_BACKEND_PATH = "custom-backend"
        const val BY_DOMAIN_PATH = "by-domain"
    }
}
