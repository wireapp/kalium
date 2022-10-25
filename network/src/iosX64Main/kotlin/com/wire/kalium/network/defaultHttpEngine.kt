package com.wire.kalium.network

import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

actual fun defaultHttpEngine(
    serverConfigDTOLinks: ServerConfigDTO.Links?,
    proxyCredentials: ProxyCredentialsDTO?
): HttpClientEngine {
    return Darwin.create {
        pipelining = true
    }
}
