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

package com.wire.kalium.network.api.base.authenticated.properties

import com.wire.kalium.network.utils.NetworkResponse

interface PropertiesApi {

    suspend fun setProperty(propertyKey: PropertyKey, propertyValue: Any): NetworkResponse<Unit>
    suspend fun deleteProperty(propertyKey: PropertyKey): NetworkResponse<Unit>

    enum class PropertyKey(val key: String) {
        WIRE_RECEIPT_MODE("WIRE_RECEIPT_MODE"),
        WIRE_TYPING_INDICATOR_MODE("WIRE_TYPING_INDICATOR_MODE")
        // TODO map other event like -ie. 'labels'-
    }
}
