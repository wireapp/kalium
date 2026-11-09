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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.CellNodeType
import com.wire.kalium.cells.domain.model.NodeIdAndVersion
import com.wire.kalium.cells.domain.model.NodeVersion
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okio.Path
import kotlin.uuid.Uuid

public interface ReplaceCellFileContentUseCase {
    /**
     * Replaces an existing file in the qualified conversation's Drive, preserving its UUID and full path.
     * [assetSize] must be greater than zero; empty replacements are rejected before any upload.
     * The caller owns [assetPath] and must keep it available until this suspending operation returns.
     * Success means the new version is published, is the current head and has a download URL.
     * Failures after uploading may leave a draft or an already published version; callers must not blindly retry.
     * Cancellation propagates to the caller. Saved offline copies are not modified by this operation.
     */
    public suspend operator fun invoke(
        conversationId: QualifiedID,
        fileUuid: String,
        assetPath: Path,
        assetSize: Long,
    ): Either<ReplaceCellFileContentFailure, NodeIdAndVersion>
}

internal class ReplaceCellFileContentUseCaseImpl(
    private val cellsRepository: CellsRepository,
    private val conversationRepository: CellConversationRepository,
) : ReplaceCellFileContentUseCase {
    @Suppress("ReturnCount")
    override suspend fun invoke(
        conversationId: QualifiedID,
        fileUuid: String,
        assetPath: Path,
        assetSize: Long,
    ): Either<ReplaceCellFileContentFailure, NodeIdAndVersion> {
        if (assetSize <= 0) return ReplaceCellFileContentFailure.InvalidFile.left()
        val cellName = conversationRepository.getCellName(QualifiedIDEntity(conversationId.value, conversationId.domain))
            .getOrElse { return ReplaceCellFileContentFailure.Other(it).left() }
        if (cellName.isNullOrBlank()) return ReplaceCellFileContentFailure.ConversationHasNoCell.left()
        val existing = cellsRepository.getNode(fileUuid)
            .getOrElse { return ReplaceCellFileContentFailure.Other(it).left() }
        if (!existing.path.startsWith("${cellName.trimEnd('/')}/")) {
            return ReplaceCellFileContentFailure.WrongConversation.left()
        }
        val isPublishedFile = existing.type == CellNodeType.FILE.value && !existing.isDraft && !existing.isRecycled
        if (existing.uuid != fileUuid || !isPublishedFile) {
            return ReplaceCellFileContentFailure.InvalidFile.left()
        }
        val previousVersions = cellsRepository.getNodeVersions(fileUuid)
            .getOrElse { return ReplaceCellFileContentFailure.Other(it).left() }.map { it.id }.toSet()
        val replacement = CellNode(
            uuid = existing.uuid,
            versionId = Uuid.random().toString(),
            path = existing.path,
            size = assetSize,
            isDraft = true,
        )
        cellsRepository.uploadFile(assetPath, replacement) { }
            .getOrElse { return ReplaceCellFileContentFailure.Other(it).left() }
        // Draft metadata can lag behind the upload. Share one deadline for
        // observing the uploaded draft, publishing it and confirming the head.
        return withTimeoutOrNull(PUBLICATION_TIMEOUT_MILLIS) {
            val uploadedVersion = awaitUploadedVersion(replacement)
                .getOrElse { return@withTimeoutOrNull it.left() }
            val version = NodeIdAndVersion(replacement.uuid, replacement.versionId)
            cellsRepository.publishDrafts(listOf(version))
                .getOrElse { return@withTimeoutOrNull ReplaceCellFileContentFailure.Other(it).left() }
            awaitPublished(replacement, uploadedVersion.hash, previousVersions)
        } ?: ReplaceCellFileContentFailure.PublicationTimeout.left()
    }

    private suspend fun awaitUploadedVersion(node: CellNode): Either<ReplaceCellFileContentFailure, NodeVersion> {
        var uploaded: NodeVersion? = null
        while (uploaded == null) {
            uploaded = cellsRepository.getNodeVersions(node.uuid)
                .getOrElse { return ReplaceCellFileContentFailure.Other(it).left() }
                .singleOrNull { it.id == node.versionId }
            if (uploaded == null) delay(POLL_INTERVAL_MILLIS)
        }
        return uploaded.right()
    }

    @Suppress("ReturnCount")
    private suspend fun awaitPublished(
        node: CellNode,
        uploadedHash: String?,
        previousVersions: Set<String>,
    ): Either<ReplaceCellFileContentFailure, NodeIdAndVersion> {
        var published: NodeVersion? = null
        while (published == null) {
            val current = cellsRepository.getNode(node.uuid)
                .getOrElse { return ReplaceCellFileContentFailure.Other(it).left() }
            if (current.uuid != node.uuid || current.path != node.path || current.isRecycled) {
                return ReplaceCellFileContentFailure.FileChanged.left()
            }
            if (!current.isDraft) {
                val versions = cellsRepository.getNodeVersions(node.uuid)
                    .getOrElse { return ReplaceCellFileContentFailure.Other(it).left() }
                published = versions.singleOrNull { it.isPublishedReplacement(node, uploadedHash, previousVersions, current) }
            }
            if (published == null) delay(POLL_INTERVAL_MILLIS)
        }
        return NodeIdAndVersion(node.uuid, published.id).right()
    }

    private fun NodeVersion.isPublishedReplacement(
        node: CellNode,
        uploadedHash: String?,
        previousVersions: Set<String>,
        current: CellNode,
    ): Boolean {
        // Publishing changes the draft ID and storage ETag; ContentHash identifies the uploaded bytes.
        val matchesUpload = id == node.versionId || (!uploadedHash.isNullOrBlank() && hash == uploadedHash)
        val matchesCurrent = current.versionId == id ||
            (current.versionId.isBlank() && !current.eTag.isNullOrBlank() && current.eTag == etag)
        return id !in previousVersions && matchesUpload && matchesCurrent && isDraft != true &&
            isHead != false && !getUrl?.url.isNullOrBlank()
    }

    private companion object {
        const val PUBLICATION_TIMEOUT_MILLIS = 60_000L
        const val POLL_INTERVAL_MILLIS = 500L
    }
}

public sealed interface ReplaceCellFileContentFailure {
    public data object ConversationHasNoCell : ReplaceCellFileContentFailure
    public data object WrongConversation : ReplaceCellFileContentFailure
    public data object InvalidFile : ReplaceCellFileContentFailure
    public data object FileChanged : ReplaceCellFileContentFailure
    public data object PublicationTimeout : ReplaceCellFileContentFailure
    public data class Other(val cause: CoreFailure) : ReplaceCellFileContentFailure
}
