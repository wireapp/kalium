package com.wire.kalium.network

import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine

expect fun defaultHttpEngine(serverConfigDTOLinks: ServerConfigDTO.Links?): HttpClientEngine
