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
import com.wire.kalium.cells.data.model.GetNodesResponseDTO
import com.wire.kalium.cells.data.model.NodeVersionDTO
import com.wire.kalium.cells.data.model.PreCheckResultDTO
import com.wire.kalium.cells.data.model.editorUrl
import com.wire.kalium.cells.data.model.toDto
import com.wire.kalium.cells.domain.CellsApi
import com.wire.kalium.cells.domain.model.PublicLink
import com.wire.kalium.cells.domain.model.toTreeNodeType
import com.wire.kalium.cells.sdk.kmp.api.NodeServiceApi
import com.wire.kalium.cells.sdk.kmp.infrastructure.HttpResponse
import com.wire.kalium.cells.sdk.kmp.model.JobsTaskStatus
import com.wire.kalium.cells.sdk.kmp.model.LookupFilterMetaFilter
import com.wire.kalium.cells.sdk.kmp.model.LookupFilterMetaFilterOp
import com.wire.kalium.cells.sdk.kmp.model.LookupFilterStatusFilter
import com.wire.kalium.cells.sdk.kmp.model.LookupFilterTextSearch
import com.wire.kalium.cells.sdk.kmp.model.LookupFilterTextSearchIn
import com.wire.kalium.cells.sdk.kmp.model.RestActionOptionsCopyMove
import com.wire.kalium.cells.sdk.kmp.model.RestActionOptionsDelete
import com.wire.kalium.cells.sdk.kmp.model.RestActionParameters
import com.wire.kalium.cells.sdk.kmp.model.RestCreateCheckRequest
import com.wire.kalium.cells.sdk.kmp.model.RestCreateRequest
import com.wire.kalium.cells.sdk.kmp.model.RestFlag
import com.wire.kalium.cells.sdk.kmp.model.RestIncomingNode
import com.wire.kalium.cells.sdk.kmp.model.RestLookupFilter
import com.wire.kalium.cells.sdk.kmp.model.RestLookupRequest
import com.wire.kalium.cells.sdk.kmp.model.RestLookupScope
import com.wire.kalium.cells.sdk.kmp.model.RestMetaUpdate
import com.wire.kalium.cells.sdk.kmp.model.RestMetaUpdateOp
import com.wire.kalium.cells.sdk.kmp.model.RestNodeLocator
import com.wire.kalium.cells.sdk.kmp.model.RestNodeUpdates
import com.wire.kalium.cells.sdk.kmp.model.RestNodeVersionsFilter
import com.wire.kalium.cells.sdk.kmp.model.RestPromoteParameters
import com.wire.kalium.cells.sdk.kmp.model.RestPublicLinkRequest
import com.wire.kalium.cells.sdk.kmp.model.RestShareLink
import com.wire.kalium.cells.sdk.kmp.model.RestShareLinkAccessType
import com.wire.kalium.cells.sdk.kmp.model.RestUserMeta
import com.wire.kalium.cells.sdk.kmp.model.StatusFilterDeletedStatus
import com.wire.kalium.cells.sdk.kmp.model.TreeNodeType
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

@Suppress("TooManyFunctions")
internal class CellsApiImpl(
    private val getNodeServiceApi: suspend () -> NodeServiceApi,
) : CellsApi {

    @Suppress("MagicNumber")
    private companion object {
        private const val AWAIT_TIMEOUT = "5s"

        private fun Long.toClientTime() = this * 1000
        private fun Long.toServerTime() = this / 1000
    }

    override suspend fun getNode(uuid: String): NetworkResponse<CellNodeDTO> =
        wrapCellsResponse {
            getNodeServiceApi().getByUuid(uuid)
        }.mapSuccess { response -> response.toDto() }

    override suspend fun getNodeEditorUrl(uuid: String, urlKey: String): NetworkResponse<String> =
        wrapCellsResponse {
            getNodeServiceApi().getByUuid(uuid, listOf(NodeServiceApi.FlagsGetByUuid.WithEditorURLs))
        }.mapSuccess { response -> response.editorUrl(urlKey) ?: "" }

    override suspend fun getNodes(
        query: String,
        limit: Int,
        offset: Int,
        fileFilters: FileFilters,
        sortingSpec: SortingSpec,
    ): NetworkResponse<GetNodesResponseDTO> =
        wrapCellsResponse {
            val metadataFilters = fileFilters.tags.toMetaDataFilters(MetadataKeys.TAGS) +
                    fileFilters.owners.toMetaDataFilters(MetadataKeys.OWNER_UUID) +
                    fileFilters.mimeTypes.toMimeMetaDataFilters()

            getNodeServiceApi().lookup(
                RestLookupRequest(
                    limit = limit.toString(),
                    offset = offset.toString(),
                    scope = RestLookupScope(recursive = true),
                    filters = RestLookupFilter(
                        type = TreeNodeType.LEAF,
                        text = LookupFilterTextSearch(
                            searchIn = LookupFilterTextSearchIn.BaseName,
                            term = query
                        ),
                        metadata = metadataFilters,
                        status = fileFilters.hasPublicLink?.let { LookupFilterStatusFilter(hasPublicLink = it) }
                    ),
                    sortField = sortingSpec.criteria.apiValue,
                    sortDirDesc = sortingSpec.descending,
                    flags = listOf(RestFlag.WithPreSignedURLs)
                )
            )
        }.mapSuccess { response -> response.toDto() }

    override suspend fun getNodesForPath(
        query: String,
        path: String,
        limit: Int?,
        offset: Int?,
        fileFilters: FileFilters,
        sortingSpec: SortingSpec,
    ): NetworkResponse<GetNodesResponseDTO> =
        wrapCellsResponse {
            val metadataFilters =
                fileFilters.tags.toMetaDataFilters(MetadataKeys.TAGS) +
                        fileFilters.owners.toMetaDataFilters(MetadataKeys.OWNER_UUID) +
                        fileFilters.mimeTypes.toMimeMetaDataFilters()

            getNodeServiceApi().lookup(
                RestLookupRequest(
                    limit = limit?.toString(),
                    offset = offset?.toString(),
                    scope = RestLookupScope(root = RestNodeLocator(path = path)),
                    filters = RestLookupFilter(
                        status = LookupFilterStatusFilter(
                            deleted = if (fileFilters.onlyDeleted) StatusFilterDeletedStatus.Only else null,
                            hasPublicLink = fileFilters.hasPublicLink
                        ),
                        type = fileFilters.nodeType.toTreeNodeType(),
                        metadata = metadataFilters,
                        text = LookupFilterTextSearch(
                            searchIn = LookupFilterTextSearchIn.BaseName,
                            term = query.ifEmpty { null }
                        ),
                    ),
                    flags = listOf(RestFlag.WithPreSignedURLs),
                    sortField = sortingSpec.criteria.apiValue,
                    sortDirDesc = sortingSpec.descending,
                )
            )
        }.mapSuccess { response -> response.toDto() }

    override suspend fun delete(nodeUuid: String, permanentDelete: Boolean): NetworkResponse<Unit> =
        wrapCellsResponse {
            getNodeServiceApi().performAction(
                name = NodeServiceApi.NamePerformAction.delete,
                parameters = RestActionParameters(
                    nodes = listOf(RestNodeLocator(uuid = nodeUuid)),
                    deleteOptions = RestActionOptionsDelete(permanentDelete = permanentDelete),
                )
            )
        }.mapSuccess {}

    override suspend fun delete(paths: List<String>, permanentDelete: Boolean): NetworkResponse<Unit> =
        wrapCellsResponse {
            getNodeServiceApi().performAction(
                name = NodeServiceApi.NamePerformAction.delete,
                parameters = RestActionParameters(
                    nodes = paths.map { RestNodeLocator(it) },
                    deleteOptions = RestActionOptionsDelete(permanentDelete = permanentDelete),
                )
            )
        }.mapSuccess {}

    override suspend fun publishDraft(nodeUuid: String, versionId: String): NetworkResponse<Unit> =
        wrapCellsResponse {
            getNodeServiceApi().promoteVersion(nodeUuid, versionId, RestPromoteParameters(publish = true))
        }.mapSuccess {}

    override suspend fun cancelDraft(nodeUuid: String, versionUuid: String): NetworkResponse<Unit> =
        wrapCellsResponse {
            getNodeServiceApi().deleteVersion(nodeUuid, versionUuid)
        }.mapSuccess {}

    override suspend fun preCheck(path: String): NetworkResponse<PreCheckResultDTO> =
        wrapCellsResponse {
            getNodeServiceApi().createCheck(
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

    @Suppress("ReturnCount")
    override suspend fun getPublicLink(linkUuid: String): NetworkResponse<PublicLink> =
        wrapCellsResponse {
            getNodeServiceApi().getPublicLink(linkUuid)
        }.mapSuccess { response ->
            PublicLink(
                uuid = response.uuid ?: return networkError("UUID is null"),
                url = response.linkUrl ?: return networkError("Link URL not found"),
                expiresAt = response.accessEnd?.toLongOrNull()?.toClientTime(),
                passwordRequired = response.passwordRequired ?: false,
            )
        }

    @Suppress("ReturnCount")
    override suspend fun createPublicLink(uuid: String, fileName: String): NetworkResponse<PublicLink> {
        return wrapCellsResponse {
            getNodeServiceApi().createPublicLink(
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

    private suspend fun withPublicLink(uuid: String, block: suspend (RestShareLink) -> Unit) =
        wrapCellsResponse {
            getNodeServiceApi().getPublicLink(uuid)
        }.mapSuccess { link ->
            block(link)
        }

    override suspend fun createPublicLinkPassword(linkUuid: String, password: String): NetworkResponse<Unit> =
        withPublicLink(linkUuid) { link ->
            wrapCellsResponse {
                getNodeServiceApi().updatePublicLink(
                    linkUuid = linkUuid,
                    publicLinkRequest = RestPublicLinkRequest(
                        link = link.copy(
                            passwordRequired = true
                        ),
                        createPassword = password,
                        passwordEnabled = true,
                    )
                )
            }
        }

    override suspend fun updatePublicLinkPassword(linkUuid: String, password: String): NetworkResponse<Unit> =
        withPublicLink(linkUuid) { link ->
            wrapCellsResponse {
                getNodeServiceApi().updatePublicLink(
                    linkUuid = linkUuid,
                    publicLinkRequest = RestPublicLinkRequest(
                        link = link.copy(
                            passwordRequired = true
                        ),
                        updatePassword = password,
                        passwordEnabled = true,
                    )
                )
            }
        }

    override suspend fun removePublicLinkPassword(linkUuid: String): NetworkResponse<Unit> =
        withPublicLink(linkUuid) { link ->
            wrapCellsResponse {
                getNodeServiceApi().updatePublicLink(
                    linkUuid = linkUuid,
                    publicLinkRequest = RestPublicLinkRequest(
                        link = link.copy(
                            passwordRequired = false
                        ),
                        passwordEnabled = false,
                    )
                )
            }
        }

    override suspend fun setPublicLinkExpiration(linkUuid: String, expireAt: Long?): NetworkResponse<Unit> =
        withPublicLink(linkUuid) { link ->
            wrapCellsResponse {
                getNodeServiceApi().updatePublicLink(
                    linkUuid = linkUuid,
                    publicLinkRequest = RestPublicLinkRequest(
                        link = link.copy(
                            accessEnd = expireAt?.toServerTime()?.toString()
                        ),
                        passwordEnabled = link.passwordRequired,
                    )
                )
            }
        }

    override suspend fun deletePublicLink(linkUuid: String): NetworkResponse<Unit> =
        wrapCellsResponse {
            getNodeServiceApi().deletePublicLink(linkUuid)
        }.mapSuccess {}

    override suspend fun createFolder(path: String): NetworkResponse<GetNodesResponseDTO> {
        return wrapCellsResponse {
            getNodeServiceApi().create(
                RestCreateRequest(
                    inputs = listOf(
                        RestIncomingNode(
                            locator = RestNodeLocator(path = path),
                            type = TreeNodeType.COLLECTION,
                        )
                    )
                )
            )
        }.mapSuccess { response -> response.toDto() }
    }

    override suspend fun createFile(path: String, contentType: String, templateUuid: String): NetworkResponse<GetNodesResponseDTO> =
        wrapCellsResponse {
            getNodeServiceApi().create(
                RestCreateRequest(
                    inputs = listOf(
                        RestIncomingNode(
                            locator = RestNodeLocator(path = path),
                            type = TreeNodeType.LEAF,
                            contentType = contentType,
                            templateUuid = templateUuid,
                        )
                    )
                )
            )
        }.mapSuccess { response -> response.toDto() }

    override suspend fun moveNode(
        uuid: String,
        path: String,
        targetPath: String,
    ): NetworkResponse<Unit> = wrapCellsResponse {
        getNodeServiceApi().performAction(
            name = NodeServiceApi.NamePerformAction.move,
            parameters = RestActionParameters(
                nodes = listOf(RestNodeLocator(path, uuid)),
                awaitStatus = JobsTaskStatus.Finished,
                awaitTimeout = AWAIT_TIMEOUT,
                copyMoveOptions = RestActionOptionsCopyMove(
                    targetPath = targetPath,
                    targetIsParent = true,
                )
            )
        )
    }.mapSuccess {}

    override suspend fun renameNode(
        uuid: String,
        path: String,
        targetPath: String,
    ): NetworkResponse<Unit> = wrapCellsResponse {
        getNodeServiceApi().performAction(
            name = NodeServiceApi.NamePerformAction.move,
            parameters = RestActionParameters(
                nodes = listOf(RestNodeLocator(path, uuid)),
                awaitStatus = JobsTaskStatus.Finished,
                awaitTimeout = AWAIT_TIMEOUT,
                copyMoveOptions = RestActionOptionsCopyMove(
                    targetPath = targetPath,
                    targetIsParent = false,
                )
            )
        )
    }.mapSuccess {}

    override suspend fun restoreNode(uuid: String): NetworkResponse<Unit> = wrapCellsResponse {
        getNodeServiceApi().performAction(
            name = NodeServiceApi.NamePerformAction.restore,
            parameters = RestActionParameters(
                nodes = listOf(RestNodeLocator(uuid = uuid)),
                awaitStatus = JobsTaskStatus.Finished,
                awaitTimeout = AWAIT_TIMEOUT
            )
        )
    }.mapSuccess {}

    override suspend fun updateNodeTags(uuid: String, tags: List<String>): NetworkResponse<Unit> = wrapCellsResponse {
        getNodeServiceApi().patchNode(
            uuid = uuid,
            nodeUpdates = RestNodeUpdates(
                metaUpdates = listOf(
                    RestMetaUpdate(
                        userMeta = RestUserMeta(
                            namespace = MetadataKeys.TAGS,
                            jsonValue = tags.joinToString(",").quoted()
                        ),
                        operation = RestMetaUpdateOp.PUT
                    )
                )
            )
        )
    }.mapSuccess { }

    override suspend fun removeTagsFromNode(uuid: String): NetworkResponse<Unit> = wrapCellsResponse {
        getNodeServiceApi().patchNode(
            uuid = uuid,
            nodeUpdates = RestNodeUpdates(
                metaUpdates = listOf(
                    RestMetaUpdate(
                        userMeta = RestUserMeta(
                            namespace = MetadataKeys.TAGS,
                            jsonValue = "\"\""
                        ),
                        operation = RestMetaUpdateOp.DELETE
                    )
                )
            )
        )
    }.mapSuccess { }

    override suspend fun getAllTags(): NetworkResponse<List<String>> = wrapCellsResponse {
        getNodeServiceApi().listNamespaceValues(namespace = MetadataKeys.TAGS)
    }.mapSuccess { it.propertyValues ?: emptyList() }

    override suspend fun getNodeVersions(
        uuid: String,
        query: RestNodeVersionsFilter
    ): NetworkResponse<List<NodeVersionDTO>> = wrapCellsResponse {
        getNodeServiceApi().nodeVersions(
            uuid = uuid,
            query = query
        )
    }.mapSuccess { collection ->
        collection.versions?.map { it.toDto() } ?: emptyList()
    }

    override suspend fun restoreNodeVersion(
        uuid: String,
        versionId: String,
        restPromoteParameters: RestPromoteParameters
    ): NetworkResponse<Unit> = wrapCellsResponse {
        getNodeServiceApi().promoteVersion(
            uuid = uuid,
            versionId = versionId,
            parameters = restPromoteParameters
        )
    }.mapSuccess {}

    private fun networkError(message: String) =
        NetworkResponse.Error(KaliumException.GenericError(IllegalStateException(message)))
}

private object MetadataKeys {
    const val TAGS = "usermeta-tags"
    const val OWNER_UUID = "usermeta-owner-uuid"
    const val TYPE = "mime"
}

private fun String.quoted(): String = "\"$this\""

private fun LookupFilterMetaFilter.Companion.termFilter(
    namespace: String,
    term: String,
    operation: LookupFilterMetaFilterOp? = null,
) = LookupFilterMetaFilter(
    namespace = namespace,
    term = term,
    operation = operation,
)

private fun List<String>.toMetaDataFilters(
    namespace: String,
): List<LookupFilterMetaFilter> {
    val operation = if (size == 1) LookupFilterMetaFilterOp.Must else LookupFilterMetaFilterOp.Should
    return map { value ->
        LookupFilterMetaFilter.termFilter(
            namespace = namespace,
            term = value.quoted(),
            operation = operation
        )
    }
}

private fun List<MIMEType>.toMimeMetaDataFilters(): List<LookupFilterMetaFilter> {
    val expanded = flatMap { mime -> mime.expandTerms() }

    val operation = if (expanded.size == 1) LookupFilterMetaFilterOp.Must else LookupFilterMetaFilterOp.Should

    return expanded.map { term ->
        LookupFilterMetaFilter.termFilter(
            namespace = MetadataKeys.TYPE,
            term = term,
            operation = operation
        )
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
