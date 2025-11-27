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

package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.model.ServerConfigEntity

internal fun newServerConfig(id: Int) = ServerConfigEntity(
    id = "config-$id",
    ServerConfigEntity.Links(
        api = "https://server$id-apiBaseUrl.de",
        accounts = "https://server$id-accountBaseUrl.de",
        webSocket = "https://server$id-webSocketBaseUrl.de",
        blackList = "https://server$id-blackListUrl.de",
        teams = "https://server$id-teamsUrl.de",
        website = "https://server$id-websiteUrl.de",
        title = "server$id-title",
        isOnPremises = false,
        ServerConfigEntity.ApiProxy(true, "apiProxy", 8888)
    ),
    ServerConfigEntity.MetaData(
        federation = false,
        domain = "wire-$id.com",
        apiVersion = 1
    )
)
