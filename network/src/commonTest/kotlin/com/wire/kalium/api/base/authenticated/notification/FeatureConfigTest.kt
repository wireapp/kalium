/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.api.base.authenticated.notification

import com.wire.kalium.mocks.responses.EventContentDTOJson
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.tools.KtxSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureConfigTest {

    private val json get() = KtxSerializer.json

    @Test
    fun givenPayload_whenDecoding_thenSuccess() {
        val result = json.decodeFromString(
            EventContentDTO.FeatureConfig.serializer(),
            EventContentDTOJson.validFeatureConfigUpdated.rawJson
        )
        assertEquals(result, EventContentDTOJson.validFeatureConfigUpdated.serializableData)
    }

    @Test
    fun givenPayload_whenEncoding_thenSuccessWithProperType() {
        val result = json.encodeToString(
            EventContentDTO.FeatureConfig.serializer(),
            EventContentDTOJson.validFeatureConfigUpdated.serializableData
        )
        assertEquals(result, EventContentDTOJson.validFeatureConfigUpdated.rawJson)

        // can be decoded back to the same object - requires proper type to be specified when encoding
        val decodedResult = json.decodeFromString(
            EventContentDTO.FeatureConfig.serializer(),
            result
        )
        assertEquals(decodedResult, EventContentDTOJson.validFeatureConfigUpdated.serializableData)
    }
}
