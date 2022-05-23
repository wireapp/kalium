package com.wire.kalium.api

import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.tools.WireServerDTO
import io.ktor.http.Url

val TEST_BACKEND_CONFIG =
    ServerConfigDTO(
        Url("https://test.api.com"), Url("https://test.account.com"), Url("https://test.ws.com"),
        Url("https://test.blacklist"), Url("https://test.teams.com"), Url("https://test.wire.com"), "Test Title", 1
    )


val TEST_BACKEND_Links =
    WireServerDTO.Links(
        Url("https://test.api.com"), Url("https://test.account.com"), Url("https://test.ws.com"),
        Url("https://test.blacklist"), Url("https://test.teams.com"), Url("https://test.wire.com"), "Test Title"
    )
