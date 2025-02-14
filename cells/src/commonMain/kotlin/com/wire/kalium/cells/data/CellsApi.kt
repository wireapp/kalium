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
package com.wire.kalium.cells.data

import com.wire.kalium.cells.data.model.CellNodeDTO
import com.wire.kalium.cells.data.model.GetFilesResponseDTO
import com.wire.kalium.cells.data.model.PreCheckResultDTO
import com.wire.kalium.cells.data.model.toDto
import com.wire.kalium.cells.domain.model.CellsCredentials
import com.wire.kalium.cells.sdk.kmp.api.NodeServiceApi
import com.wire.kalium.cells.sdk.kmp.infrastructure.HttpResponse
import com.wire.kalium.cells.sdk.kmp.model.RestActionParameters
import com.wire.kalium.cells.sdk.kmp.model.RestCreateCheckRequest
import com.wire.kalium.cells.sdk.kmp.model.RestIncomingNode
import com.wire.kalium.cells.sdk.kmp.model.RestLookupRequest
import com.wire.kalium.cells.sdk.kmp.model.RestNodeLocator
import com.wire.kalium.cells.sdk.kmp.model.RestNodeLocators
import com.wire.kalium.cells.sdk.kmp.model.RestNodeVersionsFilter
import com.wire.kalium.cells.sdk.kmp.model.RestPromoteParameters
import com.wire.kalium.cells.sdk.kmp.model.RestPublicLinkRequest
import com.wire.kalium.cells.sdk.kmp.model.RestShareLink
import com.wire.kalium.cells.sdk.kmp.model.RestShareLinkAccessType
import com.wire.kalium.cells.sdk.kmp.model.RestVersionCollection
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.session.installAuth
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

internal interface CellsApi {
    suspend fun getFiles(cellName: String): NetworkResponse<GetFilesResponseDTO>
    suspend fun getFiles(cellNames: List<String>): NetworkResponse<GetFilesResponseDTO>
    suspend fun delete(node: CellNodeDTO): NetworkResponse<Unit>
    suspend fun cancelDraft(nodeUuid: String, versionUuid: String): NetworkResponse<Unit>
    suspend fun publishDraft(nodeUuid: String): NetworkResponse<Unit>
    suspend fun preCheck(path: String): NetworkResponse<PreCheckResultDTO>
    suspend fun createPublicUrl(uuid: String, fileName: String): NetworkResponse<String>
}

internal class CellsApiImpl(
    private val credentials: CellsCredentials,
    httpClient: HttpClient
) : CellsApi {

    private companion object {
        const val API_VERSION = "v2"
    }

    private var nodeServiceApi: NodeServiceApi = NodeServiceApi(
        baseUrl = "${credentials.serverUrl}/$API_VERSION",
        httpClient = httpClient.config {
            installAuth(
                BearerAuthProvider(
                    loadTokens = { BearerTokens(credentials.accessToken, "") },
                    refreshTokens = { null },
                    realm = null
                )
            )
        }
    )

    override suspend fun getFiles(cellName: String): NetworkResponse<GetFilesResponseDTO> =
        wrapCellsResponse {
            nodeServiceApi.lookup(
                RestLookupRequest(
                    locators = RestNodeLocators(listOf(RestNodeLocator(path = "$cellName/*"))),
                    sortField = "Modified"
                )
            )
        }.mapSuccess { response -> response.toDto() }

    override suspend fun getFiles(cellNames: List<String>): NetworkResponse<GetFilesResponseDTO> =
        wrapCellsResponse {
            nodeServiceApi.lookup(
                RestLookupRequest(
                    locators = RestNodeLocators(
                        cellNames.map {
                            RestNodeLocator(path = "$it/*")
                        }
                    ),
                    sortField = "Modified"
                )
            )
        }.mapSuccess { response -> response.toDto() }

    override suspend fun delete(node: CellNodeDTO): NetworkResponse<Unit> =
        wrapCellsResponse {
            nodeServiceApi.performAction(
                name = NodeServiceApi.NamePerformAction.delete,
                parameters = RestActionParameters(
                    nodes = listOf(RestNodeLocator(path = node.path))
                )
            )
        }.mapSuccess {}

    override suspend fun publishDraft(uuid: String): NetworkResponse<Unit> =
        getNodeDraftVersions(uuid).mapSuccess { response ->
            wrapCellsResponse {
                val version = response.versions?.firstOrNull() ?: error("Draft version not found")
                nodeServiceApi.promoteVersion(uuid, version.versionId, RestPromoteParameters(publish = true))
            }
        }

    override suspend fun cancelDraft(nodeUuid: String, versionUuid: String): NetworkResponse<Unit> =
        wrapCellsResponse {
            nodeServiceApi.deleteVersion(nodeUuid, versionUuid)
        }.mapSuccess {}

    private suspend fun getNodeDraftVersions(uuid: String): NetworkResponse<RestVersionCollection> =
        wrapCellsResponse {
            nodeServiceApi.nodeVersions(uuid, RestNodeVersionsFilter())
        }

    override suspend fun preCheck(path: String): NetworkResponse<PreCheckResultDTO> =
        wrapCellsResponse {
            nodeServiceApi.createCheck(
                RestCreateCheckRequest(
                    inputs = listOf(RestIncomingNode(locator = RestNodeLocator(path))),
                    findAvailablePath = true
                )
            )
        }.mapSuccess { response ->
            response.results?.firstOrNull()?.let {
                PreCheckResultDTO(
                    fileExists = it.exists ?: false,
                    nextPath = it.nextPath,
                )
            } ?: PreCheckResultDTO()
        }

    override suspend fun createPublicUrl(uuid: String, fileName: String): NetworkResponse<String> {
        return wrapCellsResponse {
            nodeServiceApi.createPublicLink(
                uuid = uuid,
                publicLinkRequest = RestPublicLinkRequest(
                    link = RestShareLink(
                        label = fileName,
                        permissions = listOf(
                            RestShareLinkAccessType.Preview,
                            RestShareLinkAccessType.Download,
                        )
                    ),
                )
            )
        }.mapSuccess { response ->
            response.linkUrl?.let { "${credentials.serverUrl}$it" } ?: error("Link URL not found")
        }
    }

}

@Suppress("TooGenericExceptionCaught")
private suspend inline fun <reified BodyType : Any> wrapCellsResponse(
    performRequest: () -> HttpResponse<BodyType>
): NetworkResponse<BodyType> =
    try {

        val response = performRequest()
        val status = HttpStatusCode.fromValue(response.status)

        if (status.isSuccess()) {
            NetworkResponse.Success(response.body(), emptyMap(), response.status)
        } else {
            NetworkResponse.Error(KaliumException.ServerError(ErrorResponse(response.status, "", "")))
        }

    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        NetworkResponse.Error(KaliumException.GenericError(e))
    }
