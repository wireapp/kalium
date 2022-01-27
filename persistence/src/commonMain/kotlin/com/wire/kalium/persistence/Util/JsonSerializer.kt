package com.wire.kalium.persistence.Util

import kotlinx.serialization.json.Json

object JsonSerializer  {
    operator fun invoke() = Json {
        encodeDefaults = true
    }
}
