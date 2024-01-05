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

package com.wire.kalium.network.api.base.unbound.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * data class representing the remote server config json
 */
@Serializable
internal data class ServerConfigResponse(
    @SerialName("endpoints") val endpoints: EndPoints,
    @SerialName("title") val title: String,
    @SerialName("apiProxy") val apiProxy: ApiProxy?
)

@Serializable
internal data class EndPoints(
    @SerialName("backendURL") val apiBaseUrl: String,
    @SerialName("backendWSURL") val webSocketBaseUrl: String,
    @SerialName("blackListURL") val blackListUrl: String,
    @SerialName("teamsURL") val teamsUrl: String,
    @SerialName("accountsURL") val accountsBaseUrl: String,
    @SerialName("websiteURL") val websiteUrl: String
)

@Serializable
data class ApiProxy(
    @SerialName("needsAuthentication") val needsAuthentication: Boolean,
    @SerialName("host") val host: String,
    @SerialName("port") val port: Int
)
