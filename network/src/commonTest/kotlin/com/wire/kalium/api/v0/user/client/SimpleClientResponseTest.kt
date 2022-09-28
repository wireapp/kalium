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
