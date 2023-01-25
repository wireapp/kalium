package com.wire.kalium.persistence.util

import kotlinx.serialization.json.Json

object JsonSerializer {

    private val instance: Json by lazy {
        Json {
            encodeDefaults = true
            explicitNulls = false
            // to enable the serialization of maps with complex keys
            // e.g. Map<QualifiedIDEntity, PersistenceSession>
            allowStructuredMapKeys = true
        }
    }

    operator fun invoke() = instance
}
