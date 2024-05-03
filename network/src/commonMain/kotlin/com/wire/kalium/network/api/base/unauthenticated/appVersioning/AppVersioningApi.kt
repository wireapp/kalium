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

package com.wire.kalium.network.api.base.unauthenticated.appVersioning

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.setUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.Url

interface AppVersioningApi {
    suspend fun fetchAppVersionBlackList(blackListUrl: String): NetworkResponse<AppVersionBlackListResponse>
}

class AppVersioningApiImpl internal constructor(
    private val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : AppVersioningApi {

    private val httpClient get() = unauthenticatedNetworkClient.httpClient

    override suspend fun fetchAppVersionBlackList(blackListUrl: String): NetworkResponse<AppVersionBlackListResponse> {
        return wrapKaliumResponse<AppVersionBlackListResponse> {
            httpClient.get {
                val platformBlackListUrl = URLBuilder().apply {
                    val url = Url(blackListUrl)
                    host = url.host
                    protocol = url.protocol
                    // blackListUrl could already contain platform in path (after migrating from scala app)
                    pathSegments = if (url.pathSegments.lastOrNull() == appVersioningUrlPlatformPath()) url.pathSegments
                    else url.pathSegments + appVersioningUrlPlatformPath()
                }.buildString()

                setUrl(platformBlackListUrl)
            }
        }
    }
}
