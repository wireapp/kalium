/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.network.api.authenticated.notification

import com.wire.kalium.network.api.authenticated.featureConfigs.FeatureConfigData
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

internal val eventSerializationModule = SerializersModule {
    polymorphic(EventContentDTO::class) {
        polymorphic(FeatureConfigData::class) {
            defaultDeserializer { FeatureConfigData.Unknown.serializer() }
        }

        defaultDeserializer { EventContentDTO.Unknown.serializer() }
    }
}
