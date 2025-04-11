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
import com.wire.kalium.cells.domain.CellsApi
import com.wire.kalium.cells.domain.model.PublicLink
import com.wire.kalium.cells.sdk.kmp.api.NodeServiceApi
import com.wire.kalium.cells.sdk.kmp.infrastructure.HttpResponse
import com.wire.kalium.cells.sdk.kmp.model.RestActionParameters
import com.wire.kalium.cells.sdk.kmp.model.RestCreateCheckRequest
import com.wire.kalium.cells.sdk.kmp.model.RestFlag
import com.wire.kalium.cells.sdk.kmp.model.RestIncomingNode
import com.wire.kalium.cells.sdk.kmp.model.RestLookupRequest
import com.wire.kalium.cells.sdk.kmp.model.RestNodeLocator
import com.wire.kalium.cells.sdk.kmp.model.RestNodeLocators
import com.wire.kalium.cells.sdk.kmp.model.RestPromoteParameters
import com.wire.kalium.cells.sdk.kmp.model.RestPublicLinkRequest
import com.wire.kalium.cells.sdk.kmp.model.RestShareLink
import com.wire.kalium.cells.sdk.kmp.model.RestShareLinkAccessType
import com.wire.kalium.cells.sdk.kmp.model.TreeNodeType
import com.wire.kalium.cells.sdk.kmp.model.TreeQuery
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

internal class CellsApiImpl(
    private val nodeServiceApi: NodeServiceApi,
) : CellsApi {

    private companion object {
        // Sort lookup results by modification time
        private const val SORTED_BY = "mtime"
    }

    override suspend fun getNode(uuid: String): NetworkResponse<CellNodeDTO> =
        wrapCellsResponse {
            nodeServiceApi.getByUuid(uuid)
        }.mapSuccess { response -> response.toDto() }

    override suspend fun getFiles(query: String, limit: Int, offset: Int): NetworkResponse<GetFilesResponseDTO> =
        wrapCellsResponse {
            nodeServiceApi.lookup(
                RestLookupRequest(
                    limit = limit.toString(),
                    offset = offset.toString(),
                    query = TreeQuery(
                        fileName = query,
                        type = TreeNodeType.LEAF
                    ),
                    sortField = SORTED_BY,
                    sortDirDesc = true,
                    flags = listOf(RestFlag.WithPreSignedURLs)
                )
            )
        }.mapSuccess { response -> response.toDto() }

    override suspend fun getFilesForPath(path: String, limit: Int, offset: Int): NetworkResponse<GetFilesResponseDTO> =
        wrapCellsResponse {
            nodeServiceApi.lookup(
                RestLookupRequest(
                    limit = limit.toString(),
                    offset = offset.toString(),
                    locators = RestNodeLocators(
                        listOf(
                            RestNodeLocator(
                                path = "$path/*"
                            )
                        )
                    ),
                    sortField = SORTED_BY,
                    flags = listOf(RestFlag.WithPreSignedURLs)
                )
            )
        }.mapSuccess { response -> response.toDto() }

    override suspend fun delete(nodeUuid: String): NetworkResponse<Unit> =
        wrapCellsResponse {
            nodeServiceApi.performAction(
                name = NodeServiceApi.NamePerformAction.delete,
                parameters = RestActionParameters(
                    nodes = listOf(RestNodeLocator(uuid = nodeUuid)),
                )
            )
        }.mapSuccess {}

    override suspend fun delete(paths: List<String>): NetworkResponse<Unit> =
        wrapCellsResponse {
            nodeServiceApi.performAction(
                name = NodeServiceApi.NamePerformAction.delete,
                parameters = RestActionParameters(
                    nodes = paths.map { RestNodeLocator(it) },
                )
            )
        }.mapSuccess {}

    override suspend fun publishDraft(nodeUuid: String, versionId: String): NetworkResponse<Unit> =
        wrapCellsResponse {
            nodeServiceApi.promoteVersion(nodeUuid, versionId, RestPromoteParameters(publish = true))
        }.mapSuccess {}

    override suspend fun cancelDraft(nodeUuid: String, versionUuid: String): NetworkResponse<Unit> =
        wrapCellsResponse {
            nodeServiceApi.deleteVersion(nodeUuid, versionUuid)
        }.mapSuccess {}

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

    override suspend fun getPublicLink(linkUuid: String): NetworkResponse<String> =
        wrapCellsResponse {
            nodeServiceApi.getPublicLink(linkUuid)
        }.mapSuccess { response ->
            response.linkUrl ?: return networkError("Link URL not found")
        }

    @Suppress("ReturnCount")
    override suspend fun createPublicLink(uuid: String, fileName: String): NetworkResponse<PublicLink> {
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
            PublicLink(
                uuid = response.uuid ?: return networkError("UUID is null"),
                url = response.linkUrl ?: return networkError("Link URL not found"),
            )
        }
    }

   override suspend fun deletePublicLink(linkUuid: String): NetworkResponse<Unit> =
        wrapCellsResponse {
            nodeServiceApi.deletePublicLink(linkUuid)
        }.mapSuccess {}

    private fun networkError(message: String) =
        NetworkResponse.Error(KaliumException.GenericError(IllegalStateException(message)))
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
