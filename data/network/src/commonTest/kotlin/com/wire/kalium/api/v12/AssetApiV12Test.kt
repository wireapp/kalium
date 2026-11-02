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

package com.wire.kalium.api.v12

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.extensions.toJsonString
import com.wire.kalium.mocks.mocks.asset.AssetMocks
import com.wire.kalium.network.api.authenticated.asset.AssetMetadataRequest
import com.wire.kalium.network.api.model.AssetRetentionType
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v12.authenticated.AssetApiV12
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class AssetApiV12Test : ApiTest() {

    private val selfUserId = UserId("user_id", "domain")

    @Test
    fun givenFilenameWithUmlauts_whenUploading_thenMetadataPartIsCompleteJson() =
        assertMetadataPartIsCompleteJson("Mönchsjochhütte.txt")

    @Test
    fun givenFilenameWithDecomposedUmlaut_whenUploading_thenMetadataPartIsCompleteJson() =
        assertMetadataPartIsCompleteJson("Mönchsjoch.txt")

    @Test
    fun givenFilenameWithEszettAndUmlaut_whenUploading_thenMetadataPartIsCompleteJson() =
        assertMetadataPartIsCompleteJson("Straße_Ärger.pdf")

    @Test
    fun givenFilenameWithEmoji_whenUploading_thenMetadataPartIsCompleteJson() =
        assertMetadataPartIsCompleteJson("party_🎉.png")

    @Test
    fun givenFilenameWithQuoteAndBackslash_whenUploading_thenMetadataPartIsValidJson() =
        assertMetadataPartIsCompleteJson("Bericht \"Q3\"\\a.pdf")

    @Test
    fun givenAsciiFilename_whenUploading_thenMetadataPartIsCompleteJson() =
        assertMetadataPartIsCompleteJson("Moenchsjochhuette.txt")

    private fun assertMetadataPartIsCompleteJson(filename: String) = runTest {
        // Given
        val metadata = AssetMetadataRequest(
            mimeType = "text/plain",
            public = false,
            retentionType = AssetRetentionType.PERSISTENT,
            md5 = "md5-hash",
            conversationId = ConversationId("conv_id", "domain"),
            filename = filename,
            filetype = "text/plain",
        )
        val encryptedData = "some-data".encodeToByteArray()
        var parsedMetadata: Map<String, String>? = null
        val networkClient = mockAuthenticatedNetworkClient(
            AssetMocks.asset.toJsonString(),
            statusCode = HttpStatusCode.Created,
            assertion = {
                val metadataJson = readMetadataPartAsBackendDoes(body.toByteArray())
                val json = Json.parseToJsonElement(metadataJson).jsonObject
                parsedMetadata = mapOf(
                    "filename" to json.getValue("filename").jsonPrimitive.content,
                    "filetype" to json.getValue("filetype").jsonPrimitive.content,
                )
            }
        )

        // When
        val response = AssetApiV12(networkClient, selfUserId)
            .uploadAsset(metadata, { Buffer().write(encryptedData) }, encryptedData.size.toLong())

        // Then
        assertTrue(response.isSuccessful())
        assertEquals(filename, parsedMetadata?.get("filename"))
        assertEquals("text/plain", parsedMetadata?.get("filetype"))
    }

    /**
     * Mirrors cargohold: reads the first part's Content-Length header and takes exactly that many bytes.
     */
    private fun readMetadataPartAsBackendDoes(body: ByteArray): String {
        val headerTerminator = "\r\n\r\n".encodeToByteArray()
        val headerEnd = body.indexOf(headerTerminator)
        val headers = body.copyOfRange(0, headerEnd).decodeToString()
        val contentLength = CONTENT_LENGTH_REGEX.find(headers)!!.groupValues[1].toInt()
        val start = headerEnd + headerTerminator.size
        return body.copyOfRange(start, start + contentLength).decodeToString()
    }

    private fun ByteArray.indexOf(sequence: ByteArray): Int =
        (0..size - sequence.size).first { offset ->
            sequence.indices.all { this[offset + it] == sequence[it] }
        }

    private companion object {
        val CONTENT_LENGTH_REGEX = Regex("Content-Length: (\\d+)")
    }
}
