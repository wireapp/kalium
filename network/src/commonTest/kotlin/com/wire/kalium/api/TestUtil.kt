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
            false
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
        false
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
