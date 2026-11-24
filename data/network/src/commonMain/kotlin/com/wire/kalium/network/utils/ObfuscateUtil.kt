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
private val apiVersionSegment = Regex("v[0-9]+")

/**
 * Static path segments that may appear in logs. Every other segment becomes `{}`, because paths carry
 * IDs, domains, client IDs, asset keys and property names.
 *
 * The set lists the segments of the path constants in the network API classes (`PATH_*`, `*_PATH`,
 * `PUSH_TOKEN`, `FEATURE_CONFIG`) and of literal paths such as `system/settings`. `LoggablePathSegmentsTest`
 * fails when a path constant has a segment that is missing here. API version segments such as `v5` stay
 * readable through [apiVersionSegment].
 */
internal val loggablePathSegments = setOf(
    "access",
    "access-token",
    "activate",
    "add-permission",
    "api-version",
    "approve",
    "apps",
    "assets",
    "await",
    "bot",
    "bots",
    "broadcast",
    "by-domain",
    "calls",
    "claim",
    "clients",
    "code",
    "collaborators",
    "commit-bundles",
    "config",
    "connections",
    "contacts",
    "conversations",
    "cookies",
    "count",
    "custom-backend",
    "email",
    "events",
    "feature-configs",
    "federation",
    "finalize-login",
    "get-by-email",
    "get-domain-registration",
    "get-members-by-ids-using-post",
    "gifs",
    "giphy",
    "groupinfo",
    "handle",
    "history",
    "initiate-login",
    "join",
    "key-packages",
    "labels",
    "last",
    "legalhold",
    "list",
    "list-clients",
    "list-connections",
    "list-ids",
    "list-prekeys",
    "list-users",
    "login",
    "logout",
    "meetings",
    "members",
    "message-timer",
    "messages",
    "metadata",
    "mls",
    "name",
    "nonce",
    "notifications",
    "one2one",
    "one2one-conversations",
    "prekeys",
    "properties",
    "properties-values",
    "proteus",
    "protocol",
    "proxy",
    "public-keys",
    "push",
    "receipt-mode",
    "register",
    "remove",
    "reset-conversation",
    "roots.pem",
    "search",
    "self",
    "send",
    "services",
    "settings",
    "sso",
    "subconversations",
    "supported-protocols",
    "system",
    "tags",
    "targeted",
    "teams",
    "time",
    "tokens",
    "transient",
    "transient-targeted",
    "trending",
    "typing",
    "upgrade-personal-to-team",
    "users",
    "verification-code",
    "whitelisted",
)

/**
 * Returns the URL in a form that is safe to log in every build: the path with each segment that is not a
 * known static segment replaced by `{}`, and every query value masked.
 * The scheme, user info, host, port and fragment are dropped, because the host names the customer backend.
 *
 * Example:
 *   https://nginz-https.example.com/v5/users/example.com/<uuid>?client=abc -> /v5/users/{}/{}?client=***
 */
fun obfuscatePath(url: Url): String {
    val path = url.segments.joinToString(separator = "/", prefix = "/") { segment ->
        val isStatic = segment.isEmpty() || segment in loggablePathSegments || apiVersionSegment.matches(segment)
        if (isStatic) segment else MASKED_SEGMENT
    }

    val params = url.parameters.entries().filter { it.value.isNotEmpty() }
    if (params.isEmpty()) return path

    val query = params.joinToString("&") { (key, _) -> "$key=$MASKED_VALUE" }

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
