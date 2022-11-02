package com.wire.kalium.network

import com.wire.kalium.network.api.base.model.ProxyCredentialsDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine

expect fun defaultHttpEngine(
    serverConfigDTOProxy: ServerConfigDTO.Proxy?,
    proxyCredentials: ProxyCredentialsDTO? = null
): HttpClientEngine
