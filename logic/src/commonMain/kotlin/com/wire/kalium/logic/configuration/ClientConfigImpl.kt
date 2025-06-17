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

package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.client.DeviceType
import io.mockative.Mockable

@Mockable
interface ClientConfig {
    fun deviceType(): DeviceType
    fun deviceModelName(): String
    fun deviceName(): String
    fun clientType(): ClientType
}

expect class ClientConfigImpl : ClientConfig {
    override fun deviceType(): DeviceType
    override fun deviceModelName(): String
    override fun deviceName(): String
    override fun clientType(): ClientType
}
