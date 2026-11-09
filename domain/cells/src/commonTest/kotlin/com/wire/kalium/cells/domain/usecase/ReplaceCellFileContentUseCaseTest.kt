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
import com.wire.kalium.cells.domain.model.NodeIdAndVersion
import com.wire.kalium.cells.domain.model.NodeVersion
import com.wire.kalium.cells.domain.model.PreSignedUrl
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReplaceCellFileContentUseCaseTest {
    @Test
    fun givenExistingPdf_whenReplacing_thenKeepIdentityAndWaitForPublishedDownloadableVersion() = runTest {
        val fixture = Fixture()
        val result = fixture.replace()
        val uploaded = assertNotNull(fixture.uploaded)
        assertEquals("file-id", uploaded.uuid)
        assertEquals("cell/folder/report.pdf", uploaded.path)
        assertNotEquals("old-version", uploaded.versionId)
        assertTrue(uploaded.isDraft)
        assertEquals(42L, uploaded.size)
        assertEquals(listOf(NodeIdAndVersion(uploaded.uuid, uploaded.versionId)), fixture.published)
        assertEquals(NodeIdAndVersion(uploaded.uuid, uploaded.versionId).right(), result)
        assertTrue(fixture.versionReads >= 2)
    }

    @Test
    fun givenEmptyOrNegativeSize_whenReplacing_thenRejectWithoutReadingOrUploading() = runTest {
        val operation = ReplaceCellFileContentUseCaseImpl(mock<CellsRepository>(), mock<CellConversationRepository>())
        for (size in listOf(0L, -1L)) {
            val result = operation(ConversationId("conversation", "example.com"), "file-id", "replacement.pdf".toPath(), size)
            assertEquals(ReplaceCellFileContentFailure.InvalidFile.left(), result)
        }
    }

    @Test
    fun givenOneByteReplacement_whenReplacing_thenPublishSmallestNonEmptyFile() = runTest {
        val fixture = Fixture()
        assertTrue(fixture.replace(assetSize = 1L) is Either.Right)
        assertEquals(1L, assertNotNull(fixture.uploaded).size)
    }

    @Test
    fun givenUploadStillRunning_whenReplacing_thenDoNotPublishOrReturn() = runTest {
        val fixture = Fixture()
        fixture.uploadGate = CompletableDeferred()
        val replacement = async { fixture.replace() }
        fixture.uploadStarted.await()
        assertFalse(replacement.isCompleted)
        assertTrue(fixture.published.isEmpty())
        fixture.uploadGate?.complete(Unit)
        assertTrue(replacement.await() is Either.Right)
    }

    @Test
    fun givenSiblingCellPath_whenReplacing_thenRejectWithoutUpload() = runTest {
        val fixture = Fixture()
        fixture.existing = fixture.existing.copy(path = "cell-other/report.pdf")
        assertEquals(ReplaceCellFileContentFailure.WrongConversation.left(), fixture.replace())
        assertEquals(null, fixture.uploaded)
    }

    @Test
    fun givenNoDriveForQualifiedConversation_whenReplacing_thenRejectWithoutUpload() = runTest {
        val fixture = Fixture()
        fixture.cellName(null)
        assertEquals(ReplaceCellFileContentFailure.ConversationHasNoCell.left(), fixture.replace())
        assertEquals(null, fixture.uploaded)
    }

    @Test
    fun givenFolderDraftOrRecycledFile_whenReplacing_thenRejectWithoutUpload() = runTest {
        for (node in listOf(
            ORIGINAL.copy(type = "COLLECTION"),
            ORIGINAL.copy(isDraft = true),
            ORIGINAL.copy(isRecycled = true),
            ORIGINAL.copy(uuid = "different-id"),
        )) {
            val fixture = Fixture()
            fixture.existing = node
            assertEquals(ReplaceCellFileContentFailure.InvalidFile.left(), fixture.replace())
            assertEquals(null, fixture.uploaded)
        }
    }

    @Test
    fun givenLookupFailure_whenReplacing_thenPropagateWithoutUpload() = runTest {
        val fixture = Fixture()
        fixture.readFailure = FAILURE
        assertEquals(ReplaceCellFileContentFailure.Other(FAILURE).left(), fixture.replace())
        assertEquals(null, fixture.uploaded)
    }

    @Test
    fun givenUploadFailure_whenReplacing_thenPropagateWithoutPublishing() = runTest {
        val fixture = Fixture()
        fixture.uploadFailure = FAILURE
        assertEquals(ReplaceCellFileContentFailure.Other(FAILURE).left(), fixture.replace())
        assertTrue(fixture.published.isEmpty())
    }

    @Test
    fun givenPublicationFailure_whenReplacing_thenPropagate() = runTest {
        val fixture = Fixture()
        fixture.publishFailure = FAILURE
        assertEquals(ReplaceCellFileContentFailure.Other(FAILURE).left(), fixture.replace())
        assertEquals(2, fixture.versionReads)
    }

    @Test
    fun givenVersionReadFailure_whenReplacing_thenPropagate() = runTest {
        val fixture = Fixture()
        fixture.versionFailure = FAILURE
        assertEquals(ReplaceCellFileContentFailure.Other(FAILURE).left(), fixture.replace())
    }

    @Test
    fun givenVersionNeverBecomesDownloadable_whenReplacing_thenTimeout() = runTest {
        val fixture = Fixture()
        fixture.downloadAvailable = false
        assertEquals(ReplaceCellFileContentFailure.PublicationTimeout.left(), fixture.replace())
    }

    @Test
    fun givenPublishedHeadOmitsOptionalFlags_whenReplacing_thenConfirmPublication() = runTest {
        val fixture = Fixture()
        fixture.publishedDraft = null
        fixture.publishedHead = null
        val result = fixture.replace()
        val uploaded = assertNotNull(fixture.uploaded)
        assertEquals(NodeIdAndVersion(uploaded.uuid, uploaded.versionId).right(), result)
    }

    @Test
    fun givenHeadVersionRemainsDraft_whenReplacing_thenDoNotConfirmPublication() = runTest {
        val fixture = Fixture()
        fixture.publishedDraft = true
        assertEquals(ReplaceCellFileContentFailure.PublicationTimeout.left(), fixture.replace())
    }

    @Test
    fun givenVersionIsExplicitlyNotHead_whenReplacing_thenDoNotConfirmPublication() = runTest {
        val fixture = Fixture()
        fixture.publishedHead = false
        assertEquals(ReplaceCellFileContentFailure.PublicationTimeout.left(), fixture.replace())
    }

    @Test
    fun givenDifferentVersionIsCurrent_whenReplacing_thenDoNotConfirmPublication() = runTest {
        val fixture = Fixture()
        fixture.currentVersion = "another-version"
        assertEquals(ReplaceCellFileContentFailure.PublicationTimeout.left(), fixture.replace())
    }

    @Test
    fun givenPublishedHeadHasOnlyMatchingEtag_whenReplacing_thenConfirmPublication() = runTest {
        val fixture = Fixture()
        fixture.currentVersion = ""
        fixture.currentEtag = "replacement-etag"
        fixture.publishedEtag = "replacement-etag"
        fixture.publishedDraft = null
        fixture.publishedHead = null
        val result = fixture.replace()
        val uploaded = assertNotNull(fixture.uploaded)
        assertEquals(NodeIdAndVersion(uploaded.uuid, uploaded.versionId).right(), result)
    }

    @Test
    fun givenPublishingChangesDraftIdAndOmitsMetadata_whenReplacing_thenReturnActualPublishedVersion() = runTest {
        val fixture = Fixture()
        fixture.currentVersion = ""
        fixture.currentEtag = "replacement-etag"
        fixture.publishedEtag = "replacement-etag"
        fixture.publishedVersion = "server-published-version"
        fixture.publishedHash = "replacement-content"
        fixture.publishedDraft = null
        fixture.publishedHead = null
        assertEquals(NodeIdAndVersion("file-id", "server-published-version").right(), fixture.replace())
    }

    @Test
    fun givenPublishedIdChangesWithoutContentHash_whenReplacing_thenDoNotGuessTheNewVersion() = runTest {
        val fixture = Fixture()
        fixture.currentVersion = ""
        fixture.currentEtag = "published-storage-etag"
        fixture.publishedEtag = "published-storage-etag"
        fixture.publishedVersion = "server-published-version"
        assertEquals(ReplaceCellFileContentFailure.PublicationTimeout.left(), fixture.replace())
    }

    @Test
    fun givenPublishedVersionHasDifferentContentFromUploadedDraft_whenReplacing_thenDoNotConfirmPublication() = runTest {
        val fixture = Fixture()
        fixture.currentVersion = ""
        fixture.currentEtag = "other-content"
        fixture.uploadedHash = "replacement-content"
        fixture.publishedHash = "other-content"
        fixture.publishedEtag = "other-content"
        fixture.publishedVersion = "server-published-version"
        assertEquals(ReplaceCellFileContentFailure.PublicationTimeout.left(), fixture.replace())
    }

    @Test
    fun givenAnOldVersionHasTheSameContent_whenReplacing_thenDoNotReturnTheOldVersion() = runTest {
        val fixture = Fixture()
        fixture.existing = fixture.existing.copy(eTag = "same-content")
        fixture.currentVersion = ""
        fixture.currentEtag = "same-content"
        fixture.publishedEtag = "same-content"
        fixture.publishedHash = "same-content"
        fixture.publishedVersion = "old-version"
        assertEquals(ReplaceCellFileContentFailure.PublicationTimeout.left(), fixture.replace())
    }

    @Test
    fun givenVersionMetadataIsMissingAndEtagDiffers_whenReplacing_thenDoNotConfirmPublication() = runTest {
        val fixture = Fixture()
        fixture.currentVersion = ""
        fixture.currentEtag = "other-etag"
        fixture.publishedEtag = "replacement-etag"
        assertEquals(ReplaceCellFileContentFailure.PublicationTimeout.left(), fixture.replace())
    }

    @Test
    fun givenVersionMetadataAndEtagAreMissing_whenReplacing_thenDoNotConfirmPublication() = runTest {
        for (etag in listOf(null, "")) {
            val fixture = Fixture()
            fixture.currentVersion = ""
            fixture.currentEtag = etag
            fixture.publishedEtag = etag
            assertEquals(ReplaceCellFileContentFailure.PublicationTimeout.left(), fixture.replace())
        }
    }

    @Test
    fun givenPathChangesDuringPublication_whenReplacing_thenFail() = runTest {
        val fixture = Fixture()
        fixture.publishedPath = "cell/renamed.pdf"
        assertEquals(ReplaceCellFileContentFailure.FileChanged.left(), fixture.replace())
    }

    @Test
    fun givenCancellationDuringUpload_whenReplacing_thenPropagateWithoutPublishing() = runTest {
        val fixture = Fixture()
        fixture.uploadGate = CompletableDeferred()
        val replacement = async { fixture.replace() }
        fixture.uploadStarted.await()
        replacement.cancel(CancellationException("cancelled"))
        replacement.join()
        assertTrue(replacement.isCancelled)
        assertTrue(fixture.published.isEmpty())
    }

    @Test
    fun givenDraftMetadataIsDelayed_whenReplacing_thenWaitBeforePublishing() = runTest {
        val fixture = Fixture()
        fixture.draftReadsToSkip = 2
        assertTrue(fixture.replace() is Either.Right)
        assertTrue(testScheduler.currentTime >= 1_000)
        assertEquals(0, fixture.draftReadsToSkip)
    }

    @Test
    fun givenDraftMetadataNeverAppears_whenReplacing_thenTimeoutWithoutPublishing() = runTest {
        val fixture = Fixture()
        fixture.draftReadsToSkip = Int.MAX_VALUE
        assertEquals(ReplaceCellFileContentFailure.PublicationTimeout.left(), fixture.replace())
        assertEquals(60_000L, testScheduler.currentTime)
        assertTrue(fixture.published.isEmpty())
    }

    @Test
    fun givenDelayedDraftAndMissingDownload_whenReplacing_thenShareOneConfirmationDeadline() = runTest {
        val fixture = Fixture()
        fixture.draftReadsToSkip = 2
        fixture.downloadAvailable = false
        assertEquals(ReplaceCellFileContentFailure.PublicationTimeout.left(), fixture.replace())
        assertEquals(60_000L, testScheduler.currentTime)
        assertTrue(fixture.published.isNotEmpty())
    }

    @Test
    fun givenServerAssignsNewIdAndReportsCurrentVersion_whenReplacing_thenReturnPublishedId() = runTest {
        val fixture = Fixture()
        fixture.uploadedHash = "replacement-content"
        fixture.publishedHash = "replacement-content"
        fixture.publishedVersion = "server-published-version"
        fixture.publishedDraft = null
        fixture.publishedHead = null
        assertEquals(NodeIdAndVersion("file-id", "server-published-version").right(), fixture.replace())
    }

    @Test
    fun givenVersionReadFailsAfterUpload_whenReplacing_thenPropagateWithoutPublishing() = runTest {
        val fixture = Fixture()
        fixture.versionFailureAfterUpload = FAILURE
        assertEquals(ReplaceCellFileContentFailure.Other(FAILURE).left(), fixture.replace())
        assertTrue(fixture.published.isEmpty())
    }

    @Test
    fun givenVersionReadFailsAfterPublish_whenReplacing_thenPropagate() = runTest {
        val fixture = Fixture()
        fixture.versionFailureAfterPublish = FAILURE
        assertEquals(ReplaceCellFileContentFailure.Other(FAILURE).left(), fixture.replace())
        assertTrue(fixture.published.isNotEmpty())
    }

    private class Fixture {
        var existing = ORIGINAL
        var uploaded: CellNode? = null
        var published = emptyList<NodeIdAndVersion>()
        var versionReads = 0
        var draftReadsToSkip = 0
        var downloadAvailable = true
        var publishedDraft: Boolean? = false
        var publishedHead: Boolean? = true
        var currentVersion: String? = null
        var currentEtag: String? = null
        var uploadedEtag: String? = "draft-storage-etag"
        var uploadedHash: String? = null
        var publishedHash: String? = null
        var publishedEtag: String? = null
        var publishedVersion: String? = null
        var publishedPath: String? = null
        var readFailure: NetworkFailure? = null
        var uploadFailure: NetworkFailure? = null
        var publishFailure: NetworkFailure? = null
        var versionFailure: NetworkFailure? = null
        var versionFailureAfterUpload: NetworkFailure? = null
        var versionFailureAfterPublish: NetworkFailure? = null
        var uploadGate: CompletableDeferred<Unit>? = null
        val uploadStarted = CompletableDeferred<Unit>()
        val conversations = mock<CellConversationRepository>()
        val repository = object : CellsRepository by mock<CellsRepository>() {
            override suspend fun getNode(nodeUuid: String): Either<NetworkFailure, CellNode> =
                readFailure?.left() ?: if (published.isEmpty()) existing.right() else {
                    checkNotNull(uploaded).copy(
                        isDraft = false,
                        path = publishedPath ?: existing.path,
                        versionId = currentVersion ?: publishedVersion ?: checkNotNull(uploaded).versionId,
                        eTag = currentEtag,
                    ).right()
                }

            override suspend fun uploadFile(path: Path, node: CellNode, onProgressUpdate: (Long) -> Unit): Either<NetworkFailure, Unit> {
                uploaded = node
                assertEquals("replacement.pdf".toPath(), path)
                uploadStarted.complete(Unit)
                uploadGate?.await()
                return uploadFailure?.left() ?: Unit.right()
            }

            override suspend fun publishDrafts(nodes: List<NodeIdAndVersion>): Either<NetworkFailure, Unit> {
                published = nodes
                return publishFailure?.left() ?: Unit.right()
            }

            override suspend fun getNodeVersions(uuid: String): Either<NetworkFailure, List<NodeVersion>> {
                versionReads++
                if (published.isNotEmpty()) versionFailureAfterPublish?.let { return it.left() }
                if (uploaded != null) versionFailureAfterUpload?.let { return it.left() }
                if (uploaded != null && published.isEmpty() && draftReadsToSkip > 0) {
                    draftReadsToSkip--
                    return emptyList<NodeVersion>().right()
                }
                val version = NodeVersion(
                    id = when {
                        uploaded == null -> existing.versionId
                        published.isEmpty() -> checkNotNull(uploaded).versionId
                        else -> publishedVersion ?: checkNotNull(uploaded).versionId
                    },
                    hash = when {
                        uploaded == null -> existing.contentHash
                        published.isEmpty() -> uploadedHash ?: publishedHash
                        else -> publishedHash
                    },
                    description = null,
                    isDraft = if (published.isEmpty() || versionReads == 3) true else publishedDraft,
                    etag = when {
                        uploaded == null -> existing.eTag
                        published.isEmpty() -> uploadedEtag ?: publishedEtag
                        else -> publishedEtag
                    },
                    editorUrls = null,
                    filePreviews = null,
                    isHead = publishedHead,
                    modifiedTime = null,
                    ownerName = null,
                    ownerUuid = null,
                    getUrl = if (downloadAvailable) PreSignedUrl(null, "https://download.test/pdf") else null,
                    size = "42",
                )
                return versionFailure?.left() ?: listOf(version).right()
            }
        }

        init { cellName("cell") }

        fun cellName(name: String?) {
            everySuspend { conversations.getCellName(QualifiedIDEntity("conversation", "example.com")) } returns name.right()
        }

        suspend fun replace(assetSize: Long = 42L) = ReplaceCellFileContentUseCaseImpl(repository, conversations)(
            ConversationId("conversation", "example.com"),
            "file-id",
            "replacement.pdf".toPath(),
            assetSize,
        )
    }

    private companion object {
        val ORIGINAL = CellNode("file-id", "old-version", "cell/folder/report.pdf", type = "LEAF")
        val FAILURE = NetworkFailure.NoNetworkConnection(null)
    }
}
