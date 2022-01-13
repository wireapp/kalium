package com.wire.kalium.persistence.util

import kotlinx.serialization.json.Json

object JsonSerializer  {
    operator fun invoke() = Json {
        encodeDefaults = true
    }
}
