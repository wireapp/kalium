package com.wire.kalium.network

import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

actual fun defaultHttpEngine(serverConfigDTOLinks: ServerConfigDTO.Links?): HttpClientEngine {
    return Darwin.create {
        pipelining = true
    }
}
