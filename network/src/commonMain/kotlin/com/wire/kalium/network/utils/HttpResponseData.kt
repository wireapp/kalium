/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.network.utils

import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.util.toMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal data class HttpResponseData(
    val headers: Map<String, String?>,
    val status: HttpStatusCode,
    val body: String,
    private val json: Json
) {

    internal constructor(headers: Headers, statusCode: HttpStatusCode, body: String, json: Json) : this(
        // small issue here where keys are converted to small case letters
        // this is an issue for ktor to solve
        headers.toMap()
            .mapKeys { headerEntry -> headerEntry.key.lowercase() }
            .mapValues { headerEntry -> headerEntry.value.firstOrNull() }, // Ignore header duplication on purpose
        statusCode,
        body,
        json
    )

    private val jsonBody: JsonElement? by lazy {
        json.parseToJsonElement(body)
    }

    val jsonObject: JsonObject?
        get() = jsonBody as? JsonObject

    val jsonArray: JsonArray?
        get() = jsonBody as? JsonArray

    internal inline fun <reified T> parseBody() = json.decodeFromString<T>(body)
}
