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
package com.wire.kalium.network.cells

import com.wire.kalium.cells.sdk.kmp.api.NodeServiceApi
import com.wire.kalium.cells.sdk.kmp.model.RestActionParameters
import com.wire.kalium.cells.sdk.kmp.model.RestCreateCheckRequest
import com.wire.kalium.cells.sdk.kmp.model.RestIncomingNode
import com.wire.kalium.cells.sdk.kmp.model.RestLookupRequest
import com.wire.kalium.cells.sdk.kmp.model.RestNode
import com.wire.kalium.cells.sdk.kmp.model.RestNodeCollection
import com.wire.kalium.cells.sdk.kmp.model.RestNodeLocator
import com.wire.kalium.cells.sdk.kmp.model.RestNodeLocators
import com.wire.kalium.cells.sdk.kmp.model.RestNodeVersionsFilter
import com.wire.kalium.cells.sdk.kmp.model.RestPromoteParameters
import com.wire.kalium.cells.sdk.kmp.model.RestVersionCollection
import com.wire.kalium.network.api.unbound.cells.CellNodeDTO
import com.wire.kalium.network.api.unbound.cells.GetFilesResponseDTO
import com.wire.kalium.network.api.unbound.cells.PreCheckResultDTO
import com.wire.kalium.network.cells.aws.CellsCredentials
import com.wire.kalium.network.session.installAuth
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapCellsResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens

interface CellsApi {
    suspend fun getFiles(cellName: String): NetworkResponse<GetFilesResponseDTO>
    suspend fun delete(node: CellNodeDTO): NetworkResponse<Unit>
    suspend fun cancelDraft(node: CellNodeDTO): NetworkResponse<Unit>
    suspend fun publishDraft(node: CellNodeDTO): NetworkResponse<Unit>
    suspend fun preCheck(path: String): NetworkResponse<PreCheckResultDTO>
}

internal class CellsApiImpl(
    credentials: CellsCredentials,
    httpClient: HttpClient
) : CellsApi {

    private var nodeServiceApi: NodeServiceApi = NodeServiceApi(
        baseUrl = "${credentials.serverUrl}/a",
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
                    locators = RestNodeLocators(listOf(RestNodeLocator(path = "$cellName/*")))
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

    override suspend fun publishDraft(node: CellNodeDTO): NetworkResponse<Unit> =
        getNodeDraftVersions(node).mapSuccess { response ->
            wrapCellsResponse {
                val version = response.versions?.firstOrNull() ?: error("Draft version not found")
                nodeServiceApi.promoteVersion(node.uuid, version.versionId, RestPromoteParameters(publish = true))
            }
        }

    override suspend fun cancelDraft(node: CellNodeDTO): NetworkResponse<Unit> =
        getNodeDraftVersions(node).mapSuccess { response ->
            wrapCellsResponse {
                val version = response.versions?.firstOrNull() ?: error("Draft version not found")
                nodeServiceApi.deleteVersion(node.uuid, version.versionId)
            }
        }

    private suspend fun getNodeDraftVersions(node: CellNodeDTO): NetworkResponse<RestVersionCollection> =
        wrapCellsResponse {
            nodeServiceApi.nodeVersions(node.uuid, RestNodeVersionsFilter())
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
                    suggestedPath = it.nextPath,
                )
            } ?: PreCheckResultDTO()
        }
}

private fun RestNode.isDraft(): Boolean {
    return userMetadata?.firstOrNull { it.namespace == "usermeta-draft" }?.jsonValue == "true"
}

private fun RestNodeCollection.toDto() = GetFilesResponseDTO(
    nodes = nodes?.map { node ->
        CellNodeDTO(
            uuid = node.uuid,
            versionId = node.versionMeta?.versionId ?: "",
            path = node.path,
            type = node.type?.name ?: "",
            eTag = node.storageETag,
            isRecycleBin = node.isRecycleBin ?: false,
            isDraft = node.isDraft(),
        )
    } ?: emptyList()
)
