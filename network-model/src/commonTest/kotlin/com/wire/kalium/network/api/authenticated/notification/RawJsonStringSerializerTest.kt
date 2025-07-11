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

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RawJsonStringSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun givenJsonObject_whenUsingSerializer_thenReturnsRawJsonString() {
        val inputJson = """{"key":"value","number":42}"""
        
        val result = json.decodeFromString(RawJsonStringSerializer, inputJson)
        
        assertEquals(inputJson, result)
    }

    @Test
    fun givenJsonArray_whenUsingSerializer_thenReturnsRawJsonString() {
        val inputJson = """[{"id":1},{"id":2}]"""
        
        val result = json.decodeFromString(RawJsonStringSerializer, inputJson)
        
        assertEquals(inputJson, result)
    }

    @Test
    fun givenJsonString_whenUsingSerializer_thenReturnsRawJsonString() {
        val inputJson = """"test string""""
        
        val result = json.decodeFromString(RawJsonStringSerializer, inputJson)
        
        assertEquals(inputJson, result)
    }

    @Test
    fun givenJsonNumber_whenUsingSerializer_thenReturnsRawJsonString() {
        val inputJson = """123"""
        
        val result = json.decodeFromString(RawJsonStringSerializer, inputJson)
        
        assertEquals(inputJson, result)
    }

    @Test
    fun givenJsonBoolean_whenUsingSerializer_thenReturnsRawJsonString() {
        val inputJson = """true"""
        
        val result = json.decodeFromString(RawJsonStringSerializer, inputJson)
        
        assertEquals(inputJson, result)
    }

    @Test
    fun givenJsonNull_whenUsingSerializer_thenReturnsRawJsonString() {
        val inputJson = """null"""
        
        val result = json.decodeFromString(RawJsonStringSerializer, inputJson)
        
        assertEquals(inputJson, result)
    }

    @Test
    fun givenComplexNestedJson_whenUsingSerializer_thenReturnsRawJsonString() {
        val inputJson = """{"users":[{"name":"John","age":30},{"name":"Jane","age":25}],"count":2}"""
        
        val result = json.decodeFromString(RawJsonStringSerializer, inputJson)
        
        assertEquals(inputJson, result)
    }

    @Test
    fun givenRawJsonObjectString_whenSerializing_thenEncodesAsJsonElement() {
        val rawJsonString = """{"key":"value","number":42}"""
        
        val result = json.encodeToString(RawJsonStringSerializer, rawJsonString)
        
        assertEquals(rawJsonString, result)
    }

    @Test
    fun givenRawJsonArrayString_whenSerializing_thenEncodesAsJsonElement() {
        val rawJsonString = """[{"id":1},{"id":2}]"""
        
        val result = json.encodeToString(RawJsonStringSerializer, rawJsonString)
        
        assertEquals(rawJsonString, result)
    }

    @Test
    fun givenRawJsonPrimitiveString_whenSerializing_thenEncodesAsJsonElement() {
        val rawJsonString = """"test string""""
        
        val result = json.encodeToString(RawJsonStringSerializer, rawJsonString)
        
        assertEquals(rawJsonString, result)
    }

    @Test
    fun givenRawJsonNumberString_whenSerializing_thenEncodesAsJsonElement() {
        val rawJsonString = """123"""
        
        val result = json.encodeToString(RawJsonStringSerializer, rawJsonString)
        
        assertEquals(rawJsonString, result)
    }

    @Test
    fun givenInvalidJsonString_whenSerializing_thenThrowsSerializationException() {
        val invalidJsonString = "not valid json"

        assertFailsWith<SerializationException> {
            json.encodeToString(RawJsonStringSerializer, invalidJsonString)
        }
    }

    @Test
    fun givenIncompleteJsonString_whenSerializing_thenThrowsSerializationException() {
        val incompleteJsonString = """{"key": "value"""

        assertFailsWith<SerializationException> {
            json.encodeToString(RawJsonStringSerializer, incompleteJsonString)
        }
    }

    @Test
    fun givenRoundTripSerialization_whenSerializingAndDeserializing_thenReturnsOriginalJson() {
        val originalJson = """{"name":"John","age":30,"active":true,"score":95.5}"""
        
        // Serialize
        val serialized = json.encodeToString(RawJsonStringSerializer, originalJson)
        
        // Deserialize
        val result = json.decodeFromString(RawJsonStringSerializer, serialized)
        
        assertEquals(originalJson, result)
    }

    @Test
    fun givenDescriptor_thenHasCorrectSerialName() {
        assertEquals("RawJsonString", RawJsonStringSerializer.descriptor.serialName)
    }
}
