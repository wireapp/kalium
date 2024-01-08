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

package com.wire.kalium.api.v0.user.client

import com.wire.kalium.model.SimpleClientResponseJson
import com.wire.kalium.network.api.base.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.tools.KtxSerializer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals

class SimpleClientResponseTest {

    @Test
    fun givenAJsonWithMissingClass_whenDeserializingIt_thenHandleItByPuttingUnknownClass() = runTest {
        val jsonString = SimpleClientResponseJson.validMissingClass.rawJson

        val result = KtxSerializer.json.decodeFromString<SimpleClientResponse>(jsonString)

        assertEquals(DeviceTypeDTO.Unknown, result.deviceClass)
        assertEquals(SimpleClientResponseJson.validMissingClass.serializableData, result)
    }
    @Test
    fun givenAJsonWithGibberishClass_whenDeserializingIt_thenHandleItByPuttingUnknownClass() = runTest {
        val jsonString = SimpleClientResponseJson.validGibberishClass.rawJson

        val result = KtxSerializer.json.decodeFromString<SimpleClientResponse>(jsonString)

        assertEquals(DeviceTypeDTO.Unknown, result.deviceClass)
        assertEquals(SimpleClientResponseJson.validGibberishClass.serializableData, result)
    }
}
