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

package com.wire.kalium.cells

import com.wire.kalium.cells.data.CellConfigDataSource
import com.wire.kalium.cells.data.CellsApiImpl
import com.wire.kalium.cells.data.CellsDataSource
import com.wire.kalium.cells.data.CellsS3Client
import com.wire.kalium.cells.data.CellsS3CredentialsProvider
import com.wire.kalium.cells.data.credentialsOrThrow
import com.wire.kalium.cells.data.installCellsS3HttpTimeout
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.data.prepareUploadNode
import com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCaseImpl
import com.wire.kalium.cells.domain.usecase.GetWireCellConfigurationUseCaseImpl
import com.wire.kalium.cells.sdk.kmp.api.NodeServiceApi
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.dao.UserConfigDAO
import com.wire.kalium.persistence.dao.publiclink.PublicLinkDao
import io.ktor.client.HttpClient
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlinx.coroutines.CancellationException

/** Files supplied by a share, independent of the conversation's composer draft. */
public data class CellSharedFile(
    val path: Path,
    val name: String,
    val mimeType: String,
    val size: Long,
    val metadata: AssetContent.AssetMetadata? = null,
)

/** Uses the app's Cells transport without starting background uploads or consuming composer drafts. */
public class CellFileSharing internal constructor(
    private val repository: CellsRepository,
    private val closeTransport: () -> Unit = {},
) {
    public fun close(): Unit = closeTransport()

    /** Uploads drafts only. The caller must store a message before publishing, or discard the returned drafts. */
    @Suppress("ReturnCount")
    public suspend fun upload(
        cellName: String,
        files: List<CellSharedFile>,
        progress: (Long, Long) -> Unit = { _, _ -> },
    ): Either<NetworkFailure, List<CellAssetContent>> {
        require(files.isNotEmpty())
        val nodes = mutableListOf<CellNode>()
        var completed = false
        val total = files.sumOf { it.size }
        var done = 0L
        try {
            for (file in files) {
                // Treat a supplied filename as a basename, never as a path inside the destination cell.
                val name = file.name.substringAfterLast('/').substringAfterLast('\\')
                require(name.isNotBlank() && name != "." && name != "..")
                val requestedPath = "$cellName/$name"
                val prepared = repository.prepareUploadNode(requestedPath, file.size, nodes.map { it.path }.toSet())
                if (prepared is Either.Left) return prepared
                val node = (prepared as Either.Right).value
                nodes += node
                val result = repository.uploadFile(file.path, node) { progress(done + it.coerceIn(0, file.size), total) }
                if (result is Either.Left) return result
                done += file.size
                progress(done, total)
            }
            val attachments = nodes.zip(files) { node, file ->
                CellAssetContent(
                    id = node.uuid,
                    versionId = node.versionId,
                    mimeType = file.mimeType,
                    assetPath = node.path,
                    assetSize = file.size,
                    localPath = null,
                    previewUrl = null,
                    metadata = file.metadata,
                    transferStatus = AssetTransferStatus.NOT_DOWNLOADED,
                )
            }
            completed = true
            return Either.Right(attachments)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            return Either.Left(NetworkFailure.ServerMiscommunication(failure))
        } finally {
            if (!completed) withContext(NonCancellable) {
                withTimeoutOrNull(CLEANUP_TIMEOUT_MS) { nodes.forEach { cancelDraft(it.uuid, it.versionId) } }
            }
        }
    }

    /** Only called after the message owns the attachments, so failed publication remains retryable. */
    public suspend fun publish(attachments: List<CellAssetContent>): Either<NetworkFailure, Unit> =
        PublishAttachmentsUseCaseImpl(repository)(attachments)

    public suspend fun discard(attachments: List<CellAssetContent>) {
        withContext(NonCancellable) {
            withTimeoutOrNull(CLEANUP_TIMEOUT_MS) {
                attachments.forEach { cancelDraft(it.id, it.versionId) }
            }
        }
    }

    private suspend fun cancelDraft(id: String, version: String) {
        try {
            repository.cancelDraft(id, version)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            // Cleanup is best effort and must not replace the original upload/store outcome.
            com.wire.kalium.common.logger.kaliumLogger.w("Could not cancel shared Cells draft: ${failure::class.simpleName}")
        }
    }

    public companion object {
        private const val CLEANUP_TIMEOUT_MS = 5_000L

        /** The authenticated API client belongs to the caller; only this operation's S3 client is closed. */
        public fun create(
            apiClient: HttpClient,
            session: SessionManager,
            accessTokenApi: AccessTokenApi,
            config: UserConfigDAO,
            publicLinks: PublicLinkDao,
        ): CellFileSharing {
            val credentials = CellsCredentialsProviderImpl(GetWireCellConfigurationUseCaseImpl(CellConfigDataSource(config)))
            val transport = HttpClient { installCellsS3HttpTimeout() }
            val repository = CellsDataSource(
                CellsApiImpl {
                    NodeServiceApi(baseUrl = "${credentials.credentialsOrThrow().serverUrl}/v2", httpClient = apiClient)
                },
                publicLinks,
                CellsS3Client(
                    transport,
                    { credentials.credentialsOrThrow().serverUrl },
                    CellsS3CredentialsProvider(credentials, session, accessTokenApi)
                ),
                FileSystem.SYSTEM,
            )
            return CellFileSharing(repository, transport::close)
        }
    }
}
