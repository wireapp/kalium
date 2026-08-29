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

package com.wire.kalium.logic.data.asset

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AssetDataSourceLocalDeletionTest {

    @Test
    fun givenMissingAsset_whenDeleting_thenLookupFailureIsIgnoredAndRowDeletionContinues() = runTest {
        val arrangement = Arrangement()
        every { arrangement.assetDAO.getAssetByKey(eq(assetId)) } returns flowOf(null)

        assertEquals(Either.Right(Unit), arrangement.repository.deleteAssetLocally(assetId))
        verify(VerifyMode.not) { arrangement.fileSystem.exists(eq(assetPath)) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.assetDAO.deleteAsset(eq(assetId)) }
    }

    @Test
    fun givenFoundAssetWithMissingFile_whenDeleting_thenOnlyRowIsDeleted() = runTest {
        val arrangement = Arrangement()
        every { arrangement.assetDAO.getAssetByKey(eq(assetId)) } returns flowOf(assetEntity)
        every { arrangement.fileSystem.exists(eq(assetPath)) } returns false

        assertEquals(Either.Right(Unit), arrangement.repository.deleteAssetLocally(assetId))
        verify(VerifyMode.not) { arrangement.fileSystem.delete(eq(assetPath), eq(false)) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.assetDAO.deleteAsset(eq(assetId)) }
    }

    @Test
    fun givenFoundAssetWithExistingFile_whenDeleting_thenFileIsDeletedBeforeRow() = runTest {
        val operations = mutableListOf<String>()
        val arrangement = Arrangement()
        every { arrangement.assetDAO.getAssetByKey(eq(assetId)) } calls {
            operations += "lookup"
            flowOf(assetEntity)
        }
        every { arrangement.fileSystem.exists(eq(assetPath)) } calls {
            operations += "exists"
            true
        }
        every { arrangement.fileSystem.delete(eq(assetPath), eq(false)) } calls {
            operations += "file"
        }
        everySuspend { arrangement.assetDAO.deleteAsset(eq(assetId)) } calls {
            operations += "row"
        }

        assertEquals(Either.Right(Unit), arrangement.repository.deleteAssetLocally(assetId))
        assertEquals(listOf("lookup", "exists", "file", "row"), operations)
    }

    @Test
    fun givenLookupThrows_whenDeleting_thenWrappedLookupFailureIsIgnoredAndRowDeletionContinues() = runTest {
        val expected = IllegalStateException("lookup failed")
        val arrangement = Arrangement()
        every { arrangement.assetDAO.getAssetByKey(eq(assetId)) } returns flow { throw expected }

        assertEquals(Either.Right(Unit), arrangement.repository.deleteAssetLocally(assetId))
        verify(VerifyMode.not) { arrangement.fileSystem.exists(eq(assetPath)) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.assetDAO.deleteAsset(eq(assetId)) }
    }

    @Test
    fun givenRowDeletionThrows_whenDeleting_thenFailureIsReturned() = runTest {
        val expected = IllegalStateException("row deletion failed")
        val arrangement = Arrangement()
        every { arrangement.assetDAO.getAssetByKey(eq(assetId)) } returns flowOf(null)
        everySuspend { arrangement.assetDAO.deleteAsset(eq(assetId)) } throws expected

        assertEquals(Either.Left(StorageFailure.Generic(expected)), arrangement.repository.deleteAssetLocally(assetId))
    }

    @Test
    fun givenFileSystemThrows_whenDeleting_thenExceptionEscapesAndRowDeletionIsSkipped() = runTest {
        val expected = IllegalStateException("filesystem failed")
        val arrangement = Arrangement()
        every { arrangement.assetDAO.getAssetByKey(eq(assetId)) } returns flowOf(assetEntity)
        every { arrangement.fileSystem.exists(eq(assetPath)) } throws expected

        val actual = assertFailsWith<IllegalStateException> { arrangement.repository.deleteAssetLocally(assetId) }

        assertSame(expected, actual)
        verifySuspend(VerifyMode.not) { arrangement.assetDAO.deleteAsset(eq(assetId)) }
    }

    @Test
    fun givenFileDeletionThrows_whenDeleting_thenExceptionEscapesAndRowDeletionIsSkipped() = runTest {
        val expected = IllegalStateException("file deletion failed")
        val arrangement = Arrangement()
        every { arrangement.assetDAO.getAssetByKey(eq(assetId)) } returns flowOf(assetEntity)
        every { arrangement.fileSystem.exists(eq(assetPath)) } returns true
        every { arrangement.fileSystem.delete(eq(assetPath), eq(false)) } throws expected

        val actual = assertFailsWith<IllegalStateException> { arrangement.repository.deleteAssetLocally(assetId) }

        assertSame(expected, actual)
        verifySuspend(VerifyMode.not) { arrangement.assetDAO.deleteAsset(eq(assetId)) }
    }

    @Test
    fun givenLookupCancellation_whenDeleting_thenCancellationEscapesAndRowDeletionIsSkipped() = runTest {
        val expected = CancellationException("lookup cancelled")
        val arrangement = Arrangement()
        every { arrangement.assetDAO.getAssetByKey(eq(assetId)) } returns flow { throw expected }

        val actual = assertFailsWith<CancellationException> { arrangement.repository.deleteAssetLocally(assetId) }

        assertSame(expected, actual)
        verifySuspend(VerifyMode.not) { arrangement.assetDAO.deleteAsset(eq(assetId)) }
    }

    @Test
    fun givenRowDeletionCancellation_whenDeleting_thenCancellationEscapes() = runTest {
        val expected = CancellationException("row deletion cancelled")
        val arrangement = Arrangement()
        every { arrangement.assetDAO.getAssetByKey(eq(assetId)) } returns flowOf(null)
        everySuspend { arrangement.assetDAO.deleteAsset(eq(assetId)) } throws expected

        val actual = assertFailsWith<CancellationException> { arrangement.repository.deleteAssetLocally(assetId) }

        assertSame(expected, actual)
    }

    private class Arrangement {
        val assetDAO = mock<AssetDAO>(mode = MockMode.autoUnit)
        val fileSystem = mock<KaliumFileSystem>(mode = MockMode.autoUnit)
        val repository: AssetRepository = AssetDataSource(
            assetApi = mock<AssetApi>(mode = MockMode.autoUnit),
            assetDao = assetDAO,
            selfUserId = UserId("self", "wire.example"),
            assetMapper = mock<AssetMapper>(mode = MockMode.autoUnit),
            assetAuditLog = lazy { mock<AssetAuditFeatureHandler>(mode = MockMode.autoUnit) },
            kaliumFileSystem = fileSystem,
        )
    }

    private companion object {
        const val assetId = "asset-id"
        val assetPath = "/assets/asset-id".toPath()
        val assetEntity = AssetEntity(
            key = assetId,
            domain = "wire.example",
            dataPath = assetPath.toString(),
            dataSize = 10L,
            downloadedDate = 1L,
        )
    }
}
