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
                    sensitiveLogKeys.contains(lowerKey) -> {
                        key to "***"
                    }

                    /**
                     * NOTE:
                     * Production logs must not contain IDs or domains in clear. This JSON redactor does not
                     * mask ID fields; [obfuscatePath] removes them from logged URLs. Mask IDs centrally
                     * (redactor), not by sprinkling obfuscation across models.
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

private const val MASKED_VALUE = "***"
private const val MASKED_SEGMENT = "{}"

/** Characters of a query value kept as a debugging hint. Shorter values are fully masked. */
private const val QUERY_VALUE_HINT_LENGTH = 3

/**
 * Query keys whose value keeps a hint. Every other value, including codes, keys, signatures and tokens,
 * is fully masked, because a credential must never appear in a log.
 */
private val hintedQueryKeys = setOf("client", "cursor", "since")

/** Paging keys whose value stays in clear when it is only ASCII digits. */
private val pagingQueryKeys = setOf("size", "page", "limit", "offset")

/** Hosts that equal or end with one of these (on a dot boundary) are Wire-owned and stay in clear. */
private val wireHostSuffixes = listOf("wire.com", "wire.link", "zinfra.io")

/** The segment after `push/tokens` is the device push token, a credential, so it is fully masked. */
private const val PUSH_TOKENS_SEGMENT = "tokens"

/**
 * Path segments that stay readable: API words made of lowercase letters and hyphens (`conversations`,
 * `by-domain`) and version segments (`v9`). IDs, client IDs and asset keys contain digits, and domains contain dots,
 * so they never match. No Kalium path carries a handle or another user-chosen word.
 */
private val readablePathSegment = Regex("[a-z-]+|v[0-9]+")

/**
 * Returns the URL in a form that is safe to log in every build: `host/path?query`.
 * The host stays in clear when it is Wire-owned (`wire.com`, `wire.link`, `zinfra.io` and their subdomains).
 * Any other host is a customer backend and keeps a 3-character hint (`ngi`, then asterisks), or `{}` when it is
 * no longer than its hint. A URL without a host (Ktor reports `localhost` for it) starts at the path.
 * Each path segment that is not an API word is obfuscated like the logger's IDs (7 characters) and domains
 * (3 characters). Query values are masked, except paging numbers (`size`, `page`, `limit`, `offset`) made only of
 * ASCII digits, which stay in clear, and `client`, `cursor` and `since`, which keep a 3-character hint.
 * The scheme, user info, port and fragment are dropped.
 *
 * Example: `https://nginz-https.acme-corp.com/v5/users/example.com/9a1d6c0e-...?client=5f2b9e&size=20` logs as the
 * host hint `ngi***`, then `/v5/users/`, the domain hint `exa***`, `/`, the ID hint `9a1d6c0***`, and
 * `?client=5f2***&size=20`.
 */
fun obfuscatePath(url: Url): String {
    val segments = url.segments
    val path = segments.mapIndexed { index, segment ->
        when {
            segment.isEmpty() -> segment
            segments.getOrNull(index - 1) == PUSH_TOKENS_SEGMENT -> MASKED_SEGMENT
            readablePathSegment.matches(segment) -> segment
            else -> segment.obfuscatePathSegment()
        }
    }.joinToString(separator = "/", prefix = "/")
    val hostAndPath = url.host.obfuscateHost() + path

    val params = url.parameters.entries().filter { it.value.isNotEmpty() }
    if (params.isEmpty()) return hostAndPath

    val query = params.joinToString("&") { (key, values) ->
        val value = values.first()
        val lowerKey = key.lowercase()
        val loggedValue = when {
            lowerKey in hintedQueryKeys -> value.queryValueHint()
            lowerKey in pagingQueryKeys && value.isNotEmpty() && value.all { it in '0'..'9' } -> value
            else -> MASKED_VALUE
        }
        "$key=$loggedValue"
    }

    return "$hostAndPath?$query"
}

private fun String.obfuscateHost(): String {
    // Ktor parses a relative URL as http://localhost/..., so "localhost" means there was no host to log.
    if (isEmpty() || equals("localhost", ignoreCase = true)) return ""
    val isWireOwned = wireHostSuffixes.any { equals(it, ignoreCase = true) || endsWith(".$it", ignoreCase = true) }
    return if (isWireOwned) this else obfuscatePathSegment()
}

/**
 * Applies the logger's rules for IDs and domains: a segment with a dot is treated as a domain, anything else as an ID.
 * A segment no longer than its hint is replaced by `{}`, so it is never logged in full.
 */
private fun String.obfuscatePathSegment(): String {
    val encoded = encodeURLParameter()
    val obfuscated = if ('.' in encoded) encoded.obfuscateDomain() else encoded.obfuscateId()
    return if (obfuscated.removeSuffix(MASKED_VALUE) == encoded) MASKED_SEGMENT else obfuscated
}

private fun String.queryValueHint(): String =
    if (length > QUERY_VALUE_HINT_LENGTH) take(QUERY_VALUE_HINT_LENGTH).encodeURLParameter() + MASKED_VALUE else MASKED_VALUE

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
