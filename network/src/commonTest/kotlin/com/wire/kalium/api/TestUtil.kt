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

package com.wire.kalium.api

import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO

val TEST_BACKEND_CONFIG =
    ServerConfigDTO(
        id = "id",
        ServerConfigDTO.Links(
            "https://test.api.com",
            "https://test.account.com",
            "https://test.ws.com",
            "https://test.blacklist",
            "https://test.teams.com",
            "https://test.wire.com",
            "Test Title",
            false,
            null
        ),
        ServerConfigDTO.MetaData(
            false,
            ApiVersionDTO.Valid(1),
            null
        )
    )

val TEST_BACKEND_LINKS =
    ServerConfigDTO.Links(
        "https://test.api.com",
        "https://test.account.com",
        "https://test.ws.com",
        "https://test.blacklist",
        "https://test.teams.com",
        "https://test.wire.com",
        "Test Title",
        false,
        null
    )

val TEST_BACKEND =
    ServerConfigDTO(
        id = "id",
        links = TEST_BACKEND_LINKS,
        metaData = ServerConfigDTO.MetaData(
            false,
            ApiVersionDTO.Valid(0),
            domain = null
        )
    )
