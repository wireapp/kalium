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

package com.wire.kalium.cells

import com.wire.kalium.cells.sdk.kmp.api.NodeServiceApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class CellsNodeServiceCacheTest {
    @Test
    fun concurrentFirstRequestsPublishOneNodeApiAndOtherOwnersKeepTheirOwnCache() = runTest {
        val cache = CellsNodeServiceCache()
        val release = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { error("Cache tests must not make HTTP requests") })
        try {
            val api = NodeServiceApi(baseUrl = "https://example.com/v2", httpClient = client)
            var creations = 0
            val requests = List(8) {
                async { cache.getOrCreate { creations++; release.await(); api } }
            }
            runCurrent()
            assertEquals(1, creations)
            release.complete(Unit)
            requests.awaitAll().forEach { assertSame(api, it) }
            assertSame(api, cache.getOrCreate { error("Cached API must not initialize again") })
            assertNotSame(api, CellsNodeServiceCache().getOrCreate { NodeServiceApi(baseUrl =
                "https://example.com/v2", httpClient = client) })
        } finally {
            client.close()
        }
    }

    @Test
    fun failedOrCancelledCreationDoesNotPublishOrKeepTheMutexLocked() = runTest {
        val cache = CellsNodeServiceCache()
        assertFailsWith<IllegalStateException> { cache.getOrCreate { error("Unavailable credentials") } }
        val started = CompletableDeferred<Unit>()
        val suspended = CompletableDeferred<Unit>()
        val cancelled = async {
            cache.getOrCreate {
                started.complete(Unit)
                suspended.await()
                error("Cancelled construction must not finish")
            }
        }
        started.await()
        cancelled.cancelAndJoin()
        val client = HttpClient(MockEngine { error("Cache tests must not make HTTP requests") })
        try {
            val api = NodeServiceApi(baseUrl = "https://example.com/v2", httpClient = client)
            assertSame(api, cache.getOrCreate { api })
            assertSame(api, cache.getOrCreate { error("Successful retry must be cached") })
        } finally {
            client.close()
        }
    }
}
