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
package com.wire.kalium.api.v13

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.responses.ErrorResponseJson
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.model.FederationErrorResponse
import com.wire.kalium.network.api.model.MLSErrorResponse
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v13.authenticated.MLSMessageApiV13
import com.wire.kalium.network.exceptions.FederationError
import com.wire.kalium.network.exceptions.MLSError
import com.wire.kalium.network.serialization.Mls
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class MLSMessageApiV13Test : ApiTest() {

    @Test
    fun givenCommitBundleFailsWithNonFederatingBackends_whenSending_thenPreservesConflictDomains() = runTest {
        val expected = FederationErrorResponse.Conflict(listOf("backend-a.example", "backend-b.example"))
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = ErrorResponseJson.validFederationConflictingBackends(expected).rawJson,
            statusCode = HttpStatusCode.Conflict,
            assertion = {
                assertPost()
                assertContentType(ContentType.Message.Mls)
                assertPathEqual(PATH_COMMIT_BUNDLES)
            },
        )

        val result = MLSMessageApiV13(networkClient).sendCommitBundle(COMMIT_BUNDLE)

        val error = assertIs<NetworkResponse.Error>(result)
        val federationError = assertIs<FederationError>(error.kException)
        assertEquals(expected, federationError.errorResponse)
    }

    @Test
    fun givenCommitBundleFailsWithGroupOutOfSync_whenSending_thenPreservesMlsError() = runTest {
        val expected = MLSErrorResponse.GroupOutOfSync(
            missingUsers = listOf(UserId("user-id", "backend.example")),
            message = "Group is out of sync",
        )
        val networkClient = mockAuthenticatedNetworkClient(
            responseBody = KtxSerializer.json.encodeToString(MLSErrorResponse.serializer(), expected),
            statusCode = HttpStatusCode.Conflict,
            assertion = {
                assertPost()
                assertContentType(ContentType.Message.Mls)
                assertPathEqual(PATH_COMMIT_BUNDLES)
            },
        )

        val result = MLSMessageApiV13(networkClient).sendCommitBundle(COMMIT_BUNDLE)

        val error = assertIs<NetworkResponse.Error>(result)
        val mlsError = assertIs<MLSError>(error.kException)
        assertEquals(expected, mlsError.errorBody)
    }

    private companion object {
        const val PATH_COMMIT_BUNDLES = "mls/commit-bundles"
        val COMMIT_BUNDLE = MLSMessageApi.CommitBundle("CommitBundle".encodeToByteArray())
    }
}
