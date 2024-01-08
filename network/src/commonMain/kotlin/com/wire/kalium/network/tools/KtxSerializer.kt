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

package com.wire.kalium.network.tools

import com.wire.kalium.network.api.base.authenticated.notification.eventSerializationModule
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus

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

        serializersModule += eventSerializationModule
    }
}
