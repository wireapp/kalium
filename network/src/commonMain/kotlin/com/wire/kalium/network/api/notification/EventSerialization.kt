package com.wire.kalium.network.api.notification

import com.wire.kalium.network.api.featureConfigs.FeatureConfigData
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

internal val eventSerializationModule = SerializersModule {
    polymorphic(EventContentDTO::class) {
        polymorphic(FeatureConfigData::class) {
            default { FeatureConfigData.Unknown.serializer() }
        }

        default { EventContentDTO.Unknown.serializer() }
    }
}
