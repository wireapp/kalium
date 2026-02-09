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
package com.wire.kalium.cli

import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals

class SftConfigTransformerTest {

    private val sftToInject = "https://rust-sft.stars.wire.link"

    @Test
    fun givenConfigWithSftServers_whenOverwriting_thenSftIsReplaced() {
        val configWithSft = """
            {
                "ice_servers": [{"urls": ["turn:example.com"]}],
                "is_federating": true,
                "sft_servers": [{"urls": ["https://original-sft.example.com"]}],
                "sft_servers_all": [
                    {"urls": ["https://sft1.example.com"]},
                    {"urls": ["https://sft2.example.com"]}
                ],
                "ttl": 3600
            }
        """.trimIndent()

        val transformer = createSftOverrideTransformer(sftToInject)
        val result = Json.parseToJsonElement(transformer(configWithSft))
        val expected = Json.parseToJsonElement("""
            {
                "ice_servers": [{"urls": ["turn:example.com"]}],
                "is_federating": true,
                "sft_servers": [{"urls": ["$sftToInject"]}],
                "sft_servers_all": [{"urls": ["$sftToInject"]}],
                "ttl": 3600
            }
        """.trimIndent())

        assertEquals(expected, result)
    }

    @Test
    fun givenConfigWithoutSftServers_whenOverwriting_thenConfigIsUnchanged() {
        val configWithoutSft = """
            {
                "ice_servers": [{"urls": ["turn:example.com"]}],
                "is_federating": true,
                "some_other_servers": [{"urls": ["https://other.example.com"]}],
                "ttl": 3600
            }
        """.trimIndent()

        val transformer = createSftOverrideTransformer(sftToInject)
        val result = Json.parseToJsonElement(transformer(configWithoutSft))
        val expected = Json.parseToJsonElement(configWithoutSft)

        assertEquals(expected, result)
    }

    @Test
    fun givenConfigWithOnlySftServers_whenOverwriting_thenOnlyThatFieldIsReplaced() {
        val configWithOnlySftServers = """
            {
                "ice_servers": [{"urls": ["turn:example.com"]}],
                "sft_servers": [{"urls": ["https://original-sft.example.com"]}],
                "ttl": 3600
            }
        """.trimIndent()

        val transformer = createSftOverrideTransformer(sftToInject)
        val result = Json.parseToJsonElement(transformer(configWithOnlySftServers))
        val expected = Json.parseToJsonElement("""
            {
                "ice_servers": [{"urls": ["turn:example.com"]}],
                "sft_servers": [{"urls": ["$sftToInject"]}],
                "ttl": 3600
            }
        """.trimIndent())

        assertEquals(expected, result)
    }
}