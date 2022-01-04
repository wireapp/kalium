package com.wire.kalium.persistent.util

import kotlinx.serialization.json.Json

object JsonSerializer  {
    operator fun invoke() = Json {
        encodeDefaults = true
    }
}
