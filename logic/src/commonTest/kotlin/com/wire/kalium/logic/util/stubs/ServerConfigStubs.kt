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

package com.wire.kalium.logic.util.stubs

import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.model.ServerConfigEntity

internal fun newTestServer(id: Int) = ServerConfig(
    id = "config-$id",
    links = ServerConfig.Links(
        api = "https://server$id-apiBaseUrl.de",
        accounts = "https://server$id-accountBaseUrl.de",
        webSocket = "https://server$id-webSocketBaseUrl.de",
        blackList = "https://server$id-blackListUrl.de",
        teams = "https://server$id-teamsUrl.de",
        website = "https://server$id-websiteUrl.de",
        title = "server$id-title",
        false,
        null
    ),
    metaData = ServerConfig.MetaData(
        commonApiVersion = CommonApiVersionType.Valid(id),
        domain = "domain$id.com",
        federation = false
    )
)

internal fun newServerConfig(id: Int, federationEnabled: Boolean = false) = ServerConfig(
    id = "config-$id",
    links = ServerConfig.Links(
        api = "https://server$id-apiBaseUrl.de",
        accounts = "https://server$id-accountBaseUrl.de",
        webSocket = "https://server$id-webSocketBaseUrl.de",
        blackList = "https://server$id-blackListUrl.de",
        teams = "https://server$id-teamsUrl.de",
        website = "https://server$id-websiteUrl.de",
        title = "server$id-title",
        false,
        null
    ),
    metaData = ServerConfig.MetaData(
        commonApiVersion = CommonApiVersionType.Valid(id),
        domain = "domain$id.com",
        federation = federationEnabled
    )
)

internal fun newServerConfigEntity(id: Int) = ServerConfigEntity(
    id = "config-$id",
    links = ServerConfigEntity.Links(
        api = "https://server$id-apiBaseUrl.de",
        accounts = "https://server$id-accountBaseUrl.de",
        webSocket = "https://server$id-webSocketBaseUrl.de",
        blackList = "https://server$id-blackListUrl.de",
        teams = "https://server$id-teamsUrl.de",
        website = "https://server$id-websiteUrl.de",
        title = "server$id-title",
        false,
        null
    ),
    metaData = ServerConfigEntity.MetaData(
        apiVersion = id,
        domain = "domain$id.com",
        federation = false
    )
)

internal fun newServerConfigDTO(id: Int) = ServerConfigDTO(
    id = "config-$id",
    links = ServerConfigDTO.Links(
        api = "https://server$id-apiBaseUrl.de",
        accounts = "https://server$id-accountBaseUrl.de",
        webSocket = "https://server$id-webSocketBaseUrl.de",
        blackList = "https://server$id-blackListUrl.de",
        teams = "https://server$id-teamsUrl.de",
        website = "https://server$id-websiteUrl.de",
        title = "server$id-title",
        false,
        null
    ),
    ServerConfigDTO.MetaData(
        false,
        ApiVersionDTO.fromInt(id),
        domain = "domain$id.com",
    )
)
