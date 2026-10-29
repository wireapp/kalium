/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.cells

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.FakeCellsRepository
import com.wire.kalium.cells.domain.model.NodeVersion
import com.wire.kalium.cells.domain.model.NodeIdAndVersion
import com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCaseImpl
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.message.CellAssetContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PublishSharedAttachmentsTest {
    @Test
    fun retryAfterSuccessfulPublicationDoesNotPublishAgain() = runTest {
        val repository = Repository()
        val publish = PublishAttachmentsUseCaseImpl(repository)
        assertIs<Either.Right<Unit>>(publish(listOf(attachment)))
        assertIs<Either.Right<Unit>>(publish(listOf(attachment)))
        assertEquals(1, repository.calls)
    }

    @Test
    fun lostPublishResponseCanBeRetriedWithoutRepublishing() = runTest {
        val repository = Repository(loseResponse = true)
        val publish = PublishAttachmentsUseCaseImpl(repository)
        assertIs<Either.Left<NetworkFailure>>(publish(listOf(attachment)))
        assertIs<Either.Right<Unit>>(publish(listOf(attachment)))
        assertEquals(1, repository.calls)
    }

    @Test
    fun missingAttachmentVersionIsNotPublished() = runTest {
        val repository = Repository()
        repository.version = repository.version.copy(id = "another-version")
        assertIs<Either.Left<NetworkFailure>>(PublishAttachmentsUseCaseImpl(repository)(listOf(attachment)))
        assertEquals(0, repository.calls)
    }

    @Test
    fun omittedDraftFlagMeansAlreadyPublished() = runTest {
        val repository = Repository()
        repository.version = repository.version.copy(isDraft = null)
        assertIs<Either.Right<Unit>>(PublishAttachmentsUseCaseImpl(repository)(listOf(attachment)))
        assertEquals(0, repository.calls)
    }

    @Test
    fun anotherPublishedVersionDoesNotSkipPublishingTheAttachmentDraft() = runTest {
        val repository = Repository()
        repository.otherVersions = listOf(repository.version.copy(id = "older-version", isDraft = false))
        assertIs<Either.Right<Unit>>(PublishAttachmentsUseCaseImpl(repository)(listOf(attachment)))
        assertEquals(1, repository.calls)
    }

    @Test
    fun failedVersionLookupDoesNotPublish() = runTest {
        val repository = Repository()
        repository.lookupFailure = NetworkFailure.NoNetworkConnection(null)
        val result = PublishAttachmentsUseCaseImpl(repository)(listOf(attachment))
        assertEquals(repository.lookupFailure, assertIs<Either.Left<NetworkFailure>>(result).value)
        assertEquals(0, repository.calls)
    }

    private class Repository(val loseResponse: Boolean = false) : CellsRepository by FakeCellsRepository() {
        var version = NodeVersion(
            id = "version", hash = null, description = null, isDraft = true, etag = null,
            editorUrls = null, filePreviews = null, isHead = false, modifiedTime = null,
            ownerName = null, ownerUuid = null, getUrl = null, size = null,
        )
        var otherVersions = emptyList<NodeVersion>()
        var lookupFailure: NetworkFailure? = null
        var calls = 0
        override suspend fun getNodeVersions(uuid: String): Either<NetworkFailure, List<NodeVersion>> =
            lookupFailure?.let { Either.Left(it) } ?: Either.Right(otherVersions + version)
        override suspend fun publishDrafts(nodes: List<NodeIdAndVersion>): Either<NetworkFailure, Unit> {
            assertEquals(listOf(NodeIdAndVersion("node", "version")), nodes)
            calls++
            version = version.copy(isDraft = false)
            return if (loseResponse) Either.Left(NetworkFailure.NoNetworkConnection(null)) else Either.Right(Unit)
        }
    }

    private companion object {
        val attachment = CellAssetContent(
            id = "node", versionId = "version", mimeType = "text/plain", assetPath = "cell/file.txt", assetSize = 1,
            localPath = null, previewUrl = null, metadata = null, transferStatus = AssetTransferStatus.NOT_DOWNLOADED,
        )
    }
}
