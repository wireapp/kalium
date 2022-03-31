package com.wire.kalium.persistence.util

import kotlinx.serialization.json.Json

object JsonSerializer  {
    operator fun invoke() = Json {
        encodeDefaults = true

        // to enable the serialization of maps with complex keys
        // e.g. Map<QualifiedIDEntity, PersistenceSession>
        allowStructuredMapKeys = true
    }
}
