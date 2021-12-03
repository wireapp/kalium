package com.wire.kalium.network.tools

import kotlinx.serialization.json.Json

object KtxSerializer {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}
