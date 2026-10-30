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
package com.wire.kalium.linkpreview.fetch

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttpEngine
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.Protocol
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PreviewHttpEngineTest {

    @Test
    fun givenPublicDnsAnswers_whenResolving_thenReturnsOriginalAddressesWithoutAnotherLookup() {
        val addresses = listOf(address("93.184.216.34"), address("2606:4700:4700::1111"))
        val requestedHosts = mutableListOf<String>()
        val resolver = PublicPreviewDns(Dns { hostname ->
            requestedHosts += hostname
            addresses
        })

        val result = resolver.lookup("example.com")

        assertEquals(listOf("example.com"), requestedHosts)
        assertEquals(addresses, result)
    }

    @Test
    fun givenPrivateAnswerDuringConnectionLookup_whenRequesting_thenRejectsBeforeConnecting() = runTest {
        val answerSets = listOf(
            listOf(address("93.184.216.34"), address("127.0.0.1")),
            listOf(address("0:0:0:0:0:ffff:7f00:1"))
        )
        answerSets.forEach { addresses ->
            val requestedHosts = mutableListOf<String>()
            val engine = createOkHttpPreviewEngine(Dns { hostname ->
                requestedHosts += hostname
                addresses
            })
            val client = HttpClient(engine)
            try {
                assertFailsWith<UnknownHostException> { client.get("https://example.com/article") }

                assertEquals(listOf("example.com"), requestedHosts)
            } finally {
                client.close()
                engine.close()
            }
        }
    }

    @Test
    fun givenBlockedLiteralOrCredentialUrl_whenRequesting_thenRejectsWithoutDnsLookup() = runTest {
        var lookups = 0
        val engine = createOkHttpPreviewEngine(Dns {
            lookups++
            throw AssertionError("Blocked URL must be rejected before DNS lookup")
        })
        val client = HttpClient(engine)
        val urls = listOf(
            "https://127.0.0.1/",
            "https://[::1]/",
            "https://[0:0:0:0:0:0:0:1]/",
            "https://10.0.0.1/",
            "https://user:password@example.com/"
        )
        try {
            urls.forEach { url ->
                assertFailsWith<IOException>(url) { client.get(url) }
            }

            assertEquals(0, lookups)
        } finally {
            client.close()
            engine.close()
        }
    }

    @Test
    fun givenPreviewEngine_whenConfigured_thenUsesDirectConnectionsAndManualRedirects() {
        val engine = createOkHttpPreviewEngine() as OkHttpEngine
        try {
            val client = engine.config.preconfigured!!

            assertEquals(Proxy.NO_PROXY, client.proxy)
            assertFalse(client.followRedirects)
            assertFalse(client.followSslRedirects)
            assertEquals(listOf(Protocol.HTTP_1_1), client.protocols)
        } finally {
            engine.close()
        }
    }

    private fun address(value: String): InetAddress = InetAddress.getByName(value)
}
