/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.network

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class KaliumKtorCustomLoggingTest {
    @Test
    fun requestHeadersHideAuthenticationAndCookies() = runTest {
        val writer = RecordingLogWriter()
        val client = HttpClient(MockEngine { respond(content = "", status = HttpStatusCode.OK) }) {
            install(KaliumKtorCustomLogging) {
                level = LogLevel.HEADERS
                kaliumLogger = KaliumLogger(
                    config = KaliumLogger.Config(KaliumLogLevel.VERBOSE, listOf(writer)),
                    tag = "NetworkTest"
                )
            }
        }

        try {
            client.get("https://api.example.test/assets/file") {
                headers.append("Authorization", "Bearer secret-token")
                headers.append("Cookie", "session=secret-cookie")
                headers.append("pRoXy-AuThOrIzAtIoN", "Bearer secret-proxy")
                headers.append("X-Api-Key", "secret-api-key")
                headers.append("X-Request-Id", "safe-id")
            }
            val requestLog = writer.messages.single { it.startsWith("REQUEST: ") }
            val headersString = Json.parseToJsonElement(requestLog.removePrefix("REQUEST: "))
                .jsonObject["headers"]!!.jsonPrimitive.content
            val headers = Json.parseToJsonElement(headersString).jsonObject

            assertEquals("***", headers["Authorization"]?.jsonPrimitive?.content)
            assertEquals("***", headers["Cookie"]?.jsonPrimitive?.content)
            assertEquals("***", headers["pRoXy-AuThOrIzAtIoN"]?.jsonPrimitive?.content)
            assertEquals("***", headers["X-Api-Key"]?.jsonPrimitive?.content)
            assertEquals("safe-id", headers["X-Request-Id"]?.jsonPrimitive?.content)
            assertFalse(requestLog.contains("secret-"))
        } finally {
            client.close()
        }
    }

    @Test
    fun responseHeadersHideSignedRedirectAndSensitiveValues() = runTest {
        val writer = RecordingLogWriter()
        val client = HttpClient(MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(
                    "Location" to listOf("https://assets.example.test/file?Expires=123&Signature=secret-signature&Key-Pair-Id=secret-key-id"),
                    "Set-Cookie" to listOf("session=secret-cookie"),
                    "Authentication-Info" to listOf("nextnonce=secret-nonce"),
                    "X-Request-Id" to listOf("safe-id")
                )
            )
        }) {
            followRedirects = false
            install(KaliumKtorCustomLogging) {
                level = LogLevel.HEADERS
                kaliumLogger = KaliumLogger(
                    config = KaliumLogger.Config(KaliumLogLevel.VERBOSE, listOf(writer)),
                    tag = "NetworkTest"
                )
            }
        }

        try {
            client.get("https://api.example.test/assets/file")
            val responseLog = writer.messages.single { it.startsWith("RESPONSE: ") }
            val headers = Json.parseToJsonElement(responseLog.removePrefix("RESPONSE: ")).jsonObject["headers"]!!.jsonObject

            assertEquals("***", headers["Location"]?.jsonPrimitive?.content)
            assertEquals("***", headers["Set-Cookie"]?.jsonPrimitive?.content)
            assertEquals("***", headers["Authentication-Info"]?.jsonPrimitive?.content)
            assertEquals("safe-id", headers["X-Request-Id"]?.jsonPrimitive?.content)
            assertFalse(responseLog.contains("secret-"))
        } finally {
            client.close()
        }
    }

    private class RecordingLogWriter : LogWriter() {
        val messages = mutableListOf<String>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            messages += message
        }
    }
}
