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
package com.wire.kalium.cells.data

import com.wire.kalium.cells.CellsScope
import com.wire.kalium.cells.domain.model.NodePreview
import com.wire.kalium.cells.domain.usecase.GetPdfPreviewUrlUseCaseImpl
import com.wire.kalium.cells.sdk.kmp.api.NodeServiceApi
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.config.WireCellsConfigEntity
import com.wire.kalium.persistence.dao.UserConfigDAO
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class PdfPreviewIntegrationTest {
    @Test
    fun pdfUrlAndPreviewsUseActualApiDecodingAndMapping() = runTest {
        val requests = mutableListOf<String>()
        client(MIXED_PREVIEWS, requests = requests).use { client ->
            val repository = repository(client)
            assertEquals(Either.Right("pdf"), GetPdfPreviewUrlUseCaseImpl(repository)("asset"))
            assertEquals(Either.Right(listOf(NodePreview("pdf", 0, "application/pdf"), NodePreview("image", 720, "image/png"))),
                repository.getPreviews("asset"))
            assertEquals(listOf("/v2/n/node/asset", "/v2/n/node/asset"), requests)
        }
    }

    @Test
    fun absentProcessingAndFailedPdfRenditionsReturnNull() = runTest {
        listOf("[]", """[{"Processing":true}]""", """[{"Error":true}]""",
            """[{"ContentType":"image/png","PreSignedGET":{"Url":"image"}}]""").forEach { previews ->
            client(previews).use { client ->
                assertEquals(Either.Right(null), GetPdfPreviewUrlUseCaseImpl(repository(client))("asset"))
            }
        }
    }

    @Test
    fun pollingPreservesPendingAndTerminalFailureStates() = runTest {
        client("""[{"Processing":true}]""").use { assertEquals(Either.Right(null), repository(it).getPreviews("asset")) }
        client("""[{"Error":true}]""").use { assertEquals(Either.Right(emptyList()), repository(it).getPreviews("asset")) }
    }

    @Test
    fun apiFailuresAndMalformedResponsesRemainFailures() = runTest {
        listOf(HttpStatusCode.NotFound, HttpStatusCode.Forbidden, HttpStatusCode.InternalServerError).forEach { status ->
            client("[]", status).use {
                assertIs<Either.Left<NetworkFailure>>(GetPdfPreviewUrlUseCaseImpl(repository(it))("asset"))
                assertIs<Either.Left<NetworkFailure>>(repository(it).getPreviews("asset"))
            }
        }
        client("not-json").use { assertIs<Either.Left<NetworkFailure>>(GetPdfPreviewUrlUseCaseImpl(repository(it))("asset")) }
    }

    @Test
    fun cancellationEscapesApiAndPdfLookup() = runTest {
        val client = HttpClient(MockEngine { throw CancellationException("cancelled lookup") })
        try {
            assertFailsWith<CancellationException> { GetPdfPreviewUrlUseCaseImpl(repository(client))("asset") }
        } finally {
            client.close()
        }
    }

    @Test
    fun publicScopeWiresPdfLookupThroughConfiguredCellsApi() = runTest {
        val config = mock<UserConfigDAO>()
        everySuspend { config.getWireCellsConfig() } returns WireCellsConfigEntity("https://cells.test", "none", null)
        val requests = mutableListOf<String>()
        client(MIXED_PREVIEWS, requests = requests).use { client ->
            val scope = CellsScope(client, CellsScope.CellScopeDao(mock(), mock(), mock(), mock(), mock(), mock(), mock(), config),
                mock(), mock(), UserId("self", "domain"), mock())
            try {
                assertSame(scope.getPdfPreviewUrl, scope.getPdfPreviewUrl)
                assertEquals(Either.Right("pdf"), scope.getPdfPreviewUrl("asset"))
                assertEquals(listOf("/v2/n/node/asset"), requests)
            } finally {
                scope.cancel()
                scope.downloadHttpClient.close()
            }
        }
    }

    private fun client(previews: String, status: HttpStatusCode = HttpStatusCode.OK, requests: MutableList<String> = mutableListOf()) =
        HttpClient(MockEngine { request ->
            requests.add(request.url.encodedPath)
            respond("""{"Path":"document.docx","Uuid":"asset","Previews":$previews}""", status,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json() } }

    private fun repository(client: HttpClient) = CellsDataSource(
        CellsApiImpl { NodeServiceApi("https://cells.test/v2", client) }, mock(), mock(), FakeFileSystem()
    )

    private companion object {
        const val MIXED_PREVIEWS = """[
            {"ContentType":"application/pdf","PreSignedGET":{"Url":"pdf"}},
            {"ContentType":"image/png","Dimension":720,"PreSignedGET":{"Url":"image"}}
        ]"""
    }
}
