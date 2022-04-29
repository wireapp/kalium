package com.wire.kalium.api.tools.json.api.user.client

import com.wire.kalium.network.api.user.client.SimpleClientResponse
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

        assertEquals("unknown", result.deviceClass)
        assertEquals(SimpleClientResponseJson.validMissingClass.serializableData, result)
    }
}
