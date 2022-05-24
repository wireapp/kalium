package com.wire.kalium.api

import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.http.Url

val TEST_BACKEND_CONFIG =
    ServerConfigDTO(
        id = "id",
        ServerConfigDTO.Links(
            Url("https://test.api.com"), Url("https://test.account.com"), Url("https://test.ws.com"),
            Url("https://test.blacklist"), Url("https://test.teams.com"), Url("https://test.wire.com"), "Test Title"
        ),
        ServerConfigDTO.MetaData(
            false,
            ApiVersionDTO.Valid(1),
            null
        )
    )


val TEST_BACKEND_LINKS =
    ServerConfigDTO.Links(
        Url("https://test.api.com"), Url("https://test.account.com"), Url("https://test.ws.com"),
        Url("https://test.blacklist"), Url("https://test.teams.com"), Url("https://test.wire.com"), "Test Title"
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
