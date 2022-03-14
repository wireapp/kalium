package com.wire.kalium.network

import io.ktor.client.engine.*
import io.ktor.client.engine.darwin.Darwin

actual fun defaultHttpEngine(): HttpClientEngine {
    return Darwin.create {
        pipelining = true
    }
}
