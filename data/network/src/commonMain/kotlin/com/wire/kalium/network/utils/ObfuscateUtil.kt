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

import com.wire.kalium.util.serialization.toJsonElement
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.cancellation.CancellationException

fun obfuscatedJsonMessage(text: String): String = try {
    val obj = (Json.decodeFromString(text) as JsonElement)
    obfuscatedJsonElement(obj).toString()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    "\"Error while obfuscating. Content probably not json.\""
}

fun obfuscatedJsonElement(element: JsonElement): JsonElement =
    when (element) {
        is JsonPrimitive, JsonNull -> element
        is JsonArray -> {
            if (element.jsonArray.isNotEmpty()) {
                element.jsonArray.map { obfuscatedJsonElement(it) }.toJsonElement()
            } else {
                element
            }
        }

        is JsonObject -> {
            element.jsonObject.entries.associate { entry ->
                val key = entry.key
                val lowerKey = key.lowercase()

                when {
                    /**
                     * Secrets: always redact, never log in clear (tokens, cookies, auth, etc.)
                     */
                    sensitiveJsonKeys.contains(lowerKey) -> {
                        key to "***"
                    }

                    /**
                     * NOTE:
                     * IDs are allowed to be logged in clear according to the agreed policy,
                     * so we do NOT obfuscate them here anymore.
                     *
                     * If you ever need to change this policy, do it centrally (redactor),
                     * not by sprinkling obfuscation across models.
                     */

                    /**
                     * Some nested objects may still contain secrets (payload/content/etc.) - recurse.
                     */
                    sensitiveJsonObjects.contains(lowerKey) -> {
                        key to obfuscatedJsonElement(entry.value)
                    }

                    else -> {
                        key to entry.value
                    }
                }
            }.toJsonElement()
        }
    }

/**
 * Request path can be logged in clear, but query parameter values must be obfuscated.
 *
 * Example:
 *   host/path/segment?q=abc... -> host/path/segment?q=abc*** (etc.)
 */
private const val KEEP_QUERY_PREFIX = 3

/**
 * Allowlist for query keys which are safe to log in clear.
 * Everything else will have the VALUE obfuscated.
 */
private val safeQueryKeys = setOf(
    "size",
    "page",
    "limit",
    "offset",
    "cursor",
    "sort",
    "order",
    "client"
)

/**
 * Obfuscate only the VALUE of query params. Keep the PATH in clear.
 */
fun obfuscatePath(url: Url): String {
    val base = buildString {
        append(url.host)
        append(url.encodedPath)
    }

    val params = url.parameters.entries().filter { it.value.isNotEmpty() }
    if (params.isEmpty()) return base

    val query = params.joinToString("&") { (key, values) ->
        val rawValue = values.first()
        val lowerKey = key.lowercase()

        val valueToLog = if (lowerKey in safeQueryKeys) {
            rawValue.encodeURLParameter()
        } else {
            rawValue.obfuscateQueryValue().encodeURLParameterKeepingAsterisks()
        }

        "$key=$valueToLog"
    }

    return "$base?$query"
}

private fun String.encodeURLParameterKeepingAsterisks(): String =
    encodeURLParameter()
        .replace("%2A", "*")
        .replace("%2a", "*")

private fun String.obfuscateQueryValue(): String = when {
    this.isBlank() -> "***"
    this.endsWith("***") -> this
    (this.length > KEEP_QUERY_PREFIX) -> this.take(KEEP_QUERY_PREFIX) + "***"
    else -> "***"
}

fun deleteSensitiveItemsFromJson(text: String): String {
    var logMessage = ""
    try {
        val obj = (Json.decodeFromString(text) as JsonElement)
        obj.jsonObject.entries.toMutableSet().map { entry ->
            if (notSensitiveJsonArray.contains(entry.key.lowercase())) {
                if (entry.value.jsonArray.isNotEmpty()) {
                    entry.value.jsonArray[0].jsonObject.entries.toMutableSet().map { inner ->
                        if (notSensitiveJsonKeys.contains(inner.key.lowercase())) {
                            logMessage += " ${inner.key} : ${inner.value}"
                        }
                    }
                }
            }
        }
        return logMessage

    } catch (e: CancellationException) {
        throw e
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
        "access_token",
        "refresh_token",
        "token"
    )
}

private val sensitiveJsonObjects by lazy {
    listOf("qualified_id", "qualified_ids", "qualified_users", "content", "payload")
}
private val notSensitiveJsonKeys by lazy { listOf("type", "time") }
private val notSensitiveJsonArray by lazy { listOf("payload") }
