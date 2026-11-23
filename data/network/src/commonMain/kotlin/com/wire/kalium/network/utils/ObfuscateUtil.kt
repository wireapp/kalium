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
                    sensitiveLogKeys.contains(lowerKey) -> {
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
 * Query keys whose values are plain numbers and safe to log in clear. Any other value is replaced with `***`.
 */
private val numericPagingKeys = setOf("size", "page", "limit", "offset")

private const val DYNAMIC_SEGMENT = "{}"
private const val MASKED_VALUE = "***"

/**
 * A path segment is static when it is a lowercase word (letters, `-` or `_`, e.g. `conversations`, `list-ids`)
 * or an API version (`v7`). Everything else (UUIDs, domains, handles with digits or dots, hashes) is dynamic.
 * Short, letters-only dynamic values (e.g. a handle such as `alice`) still look static.
 */
private val staticSegment = Regex("[a-z][a-z_-]{0,31}|v\\d{1,3}")

/**
 * Turns a URL into a route template for logging, e.g.
 * `https://wire.example/v7/conversations/example.com/3f2a...?cursor=abc&size=10`
 * becomes `/v7/conversations/{}/{}?cursor=***&size=10`.
 * The host, user info and fragment are dropped, dynamic path segments become `{}`
 * and query values become `***`, except numeric values of paging keys.
 */
fun obfuscatePath(url: Url): String {
    val path = url.encodedPath.split('/').joinToString("/") { segment ->
        if (segment.isEmpty() || staticSegment.matches(segment)) segment else DYNAMIC_SEGMENT
    }

    val params = url.parameters.entries().filter { it.value.isNotEmpty() }
    if (params.isEmpty()) return path

    val query = params.joinToString("&") { (key, values) ->
        val value = values.first()
        val keepValue = key.lowercase() in numericPagingKeys && value.isNotEmpty() && value.all { it.isDigit() }
        "$key=${if (keepValue) value else MASKED_VALUE}"
    }

    return "$path?$query"
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

val sensitiveLogKeys by lazy {
    listOf(
        "password",
        "authorization",
        "authentication",
        "authentication-info",
        "proxy-authorization",
        "proxy-authenticate",
        "proxy-authentication-info",
        "www-authenticate",
        "set-cookie",
        "cookie",
        "location",
        "x-api-key",
        "x-auth-token",
        "x-amz-meta-user",
        "sec-websocket-key",
        "sec-websocket-accept",
        "sec-websocket-version",
        "access_token",
        "refresh_token",
        "token"
    )
}

@Deprecated("Use sensitiveLogKeys instead")
val sensitiveJsonKeys: List<String> get() = sensitiveLogKeys

private val sensitiveJsonObjects by lazy {
    listOf("qualified_id", "qualified_ids", "qualified_users", "content", "payload")
}
private val notSensitiveJsonKeys by lazy { listOf("type", "time") }
private val notSensitiveJsonArray by lazy { listOf("payload") }
