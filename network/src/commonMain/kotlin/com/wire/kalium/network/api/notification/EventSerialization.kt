package com.wire.kalium.network.api.notification

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic


internal val eventSerializationModule = SerializersModule {
    polymorphic(EventContentDTO::class) {
        default { EventContentDTO.Unknown.serializer() }
    }
}
