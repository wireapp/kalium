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

        // If API returns null or unknown values for Enums, we can use default constructor parameter to override it
        // https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md#coercing-input-values
        coerceInputValues = true
    }
}
