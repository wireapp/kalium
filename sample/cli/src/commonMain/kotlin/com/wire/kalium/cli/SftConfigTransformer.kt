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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

private const val SFT_SERVERS_KEY = "sft_servers"
private const val SFT_SERVERS_ALL_KEY = "sft_servers_all"

/**
 * Creates a call config transformer that overrides SFT server URLs.
 * This is intended for CLI testing purposes only.
 *
 * @param sftUrl The SFT server URL to inject
 * @return A transformer function that modifies the call config JSON
 */
fun createSftOverrideTransformer(sftUrl: String): (String) -> String = { configJson ->
    val jsonElement = Json.parseToJsonElement(configJson)
    overwriteSft(jsonElement, sftUrl).toString()
}

/**
 * Overwrites SFT server entries in a call configuration JSON.
 * Replaces both `sft_servers` and `sft_servers_all` with a single server entry
 * pointing to the provided URL.
 */
internal fun overwriteSft(element: JsonElement, sft: String): JsonElement =
    when (element) {
        is JsonObject -> {
            JsonObject(
                element.entries.associate { (key, value) ->
                    when (key) {
                        SFT_SERVERS_KEY, SFT_SERVERS_ALL_KEY -> {
                            key to createSftServerArray(sft)
                        }
                        else -> key to value
                    }
                }
            )
        }
        else -> element
    }

private fun createSftServerArray(sftUrl: String): JsonArray = buildJsonArray {
    add(
        buildJsonObject {
            put("urls", buildJsonArray { add(JsonPrimitive(sftUrl)) })
        }
    )
}
