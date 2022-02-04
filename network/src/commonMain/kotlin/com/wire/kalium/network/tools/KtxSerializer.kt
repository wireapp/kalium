package com.wire.kalium.network.tools

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
object KtxSerializer {
    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        // explicitNulls, defines whether null property
        // values should be included in the serialized JSON string.
        explicitNulls = false
    }
}
