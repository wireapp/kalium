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

@file:Suppress("TooGenericExceptionCaught")

package com.wire.kalium.network.utils

import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logger.obfuscateUrlPath
import com.wire.kalium.util.serialization.toJsonElement
import io.ktor.http.Url
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

fun obfuscatedJsonMessage(text: String): String = try {
    val obj = (Json.decodeFromString(text) as JsonElement)
    obfuscatedJsonElement(obj).toString()
} catch (e: Exception) {
    "\"Error while obfuscating. Content probably not json.\""
}

fun obfuscatedJsonElement(element: JsonElement): JsonElement =
    when (element) {
        is JsonPrimitive, JsonNull -> element
        is JsonArray -> {
            if (element.jsonArray.size > 0) {
                element.jsonArray.map { obfuscatedJsonElement(it) }.toJsonElement()
            } else {
                element
            }
        }

        is JsonObject -> {
            element.jsonObject.entries.associate {
                when {
                    sensitiveJsonKeys.contains(it.key.lowercase()) -> {
                        val value = "${it.value}".trim('"')
                        it.key to "${value.obfuscateId()}"
                    }

                    domainJsonKeys.contains(it.key.lowercase()) -> {
                        val value = "${it.value}".trim('"')
                        it.key to "${value.obfuscateDomain()}"
                    }

                    sensitiveJsonIdKeys.contains(it.key.lowercase()) -> {
                        val value = "${it.value}".trim('"')
                        it.key to "${value.obfuscateId()}"
                    }

                    sensitiveJsonObjects.contains(it.key.lowercase()) -> {
                        it.key to obfuscatedJsonElement(it.value)
                    }

                    else -> {
                        it.key to it.value
                    }
                }
            }.toJsonElement()
        }
    }

fun obfuscatePath(url: Url): String {
    var requestToLog = url.host
    if (url.pathSegments.isNotEmpty()) {
        url.pathSegments.map {
            requestToLog += "/${it.obfuscateUrlPath()}"
        }
    }

    if (url.parameters.entries().isNotEmpty()) {
        requestToLog += "?"
        url.parameters.entries().map {
            if (it.value.isNotEmpty()) {
                requestToLog += "${it.key}=${it.value[0].obfuscateUrlPath()}&"
            }
        }
    }

    return requestToLog.trimEnd('&')
}

fun deleteSensitiveItemsFromJson(text: String): String {
    var logMessage = ""
    try {
        val obj = (Json.decodeFromString(text) as JsonElement)
        obj.jsonObject.entries.toMutableSet().map {
            if (notSensitiveJsonArray.contains(it.key.lowercase())) {
                if (it.value.jsonArray.size > 0) {
                    it.value.jsonArray[0].jsonObject.entries.toMutableSet().map {
                        if (notSensitiveJsonKeys.contains(it.key.lowercase())) {
                            logMessage += " ${it.key} : ${it.value}"
                        }
                    }
                }
            }
        }
        return logMessage

    } catch (e: Exception) {
        return "error while logging "
    }
}

val sensitiveJsonKeys by lazy {
    listOf(
        "password",
        "authorization",
        "set-cookie",
        "cookie",
        "location",
        "x-amz-meta-user",
        "sec-websocket-key",
        "sec-websocket-accept",
        "sec-websocket-version",
        "access_token"
    )
}
private val sensitiveJsonIdKeys by lazy { listOf("conversation", "id", "user", "team", "creator_client") }
private val domainJsonKeys by lazy { listOf("domain") }
private val sensitiveJsonObjects by lazy { listOf("qualified_id", "qualified_ids", "qualified_users", "content", "payload") }
private val notSensitiveJsonKeys by lazy { listOf("type", "time") }
private val notSensitiveJsonArray by lazy { listOf("payload") }
