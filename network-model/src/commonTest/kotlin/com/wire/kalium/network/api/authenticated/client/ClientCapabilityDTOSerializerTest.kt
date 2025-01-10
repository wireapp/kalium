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
package com.wire.kalium.network.api.authenticated.client

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class ClientCapabilityDTOSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun serialize_legal_hold_implicit_consent() {
        val capability = ClientCapabilityDTO.LegalHoldImplicitConsent
        val result = json.encodeToString(ClientCapabilityDTO.serializer(), capability)
        assertEquals("\"legalhold-implicit-consent\"", result)
    }

    @Test
    fun serialize_unknown_capability() {
        val capability = ClientCapabilityDTO.Unknown("custom-capability")
        val result = json.encodeToString(ClientCapabilityDTO.serializer(), capability)
        assertEquals("\"custom-capability\"", result)
    }

    @Test
    fun deserialize_legal_hold_implicit_consent() {
        val jsonString = "\"legalhold-implicit-consent\""
        val result = json.decodeFromString(ClientCapabilityDTO.serializer(), jsonString)
        assertEquals(ClientCapabilityDTO.LegalHoldImplicitConsent, result)
    }

    @Test
    fun deserialize_unknown_capability() {
        val jsonString = "\"unknown-capability\""
        val result = json.decodeFromString(ClientCapabilityDTO.serializer(), jsonString)
        assertEquals(ClientCapabilityDTO.Unknown("unknown-capability"), result)
    }

    @Test
    fun deserialization_fails_on_invalid_json_format() {
        val invalidJson = "12345" // Invalid format for a string
        assertFailsWith<SerializationException> {
            json.decodeFromString(ClientCapabilityDTO.serializer(), invalidJson)
        }
    }
}
