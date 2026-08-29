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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetRepository
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AssetRepositoryDeleteMessageAssetCleanupTest {

    @Test
    fun givenRepositoryResult_whenCleaningUp_thenResultIsReturnedUnchanged() = runTest {
        val expected = Either.Left(StorageFailure.DataNotFound)
        val repository = mock<AssetRepository>()
        everySuspend { repository.deleteAssetLocally(eq(assetId)) } returns expected
        val cleanup = AssetRepositoryDeleteMessageAssetCleanup(repository)

        assertEquals(expected, cleanup.deleteAssetLocally(assetId))
        verifySuspend(VerifyMode.exactly(1)) { repository.deleteAssetLocally(eq(assetId)) }
    }

    @Test
    fun givenRepositoryException_whenCleaningUp_thenSameExceptionEscapes() = runTest {
        val expected = IllegalStateException("asset cleanup failed")
        val repository = mock<AssetRepository>()
        everySuspend { repository.deleteAssetLocally(eq(assetId)) } throws expected
        val cleanup = AssetRepositoryDeleteMessageAssetCleanup(repository)

        val actual = assertFailsWith<IllegalStateException> { cleanup.deleteAssetLocally(assetId) }

        assertSame(expected, actual)
    }

    @Test
    fun givenRepositoryCancellation_whenCleaningUp_thenSameCancellationEscapes() = runTest {
        val expected = CancellationException("asset cleanup cancelled")
        val repository = mock<AssetRepository>()
        everySuspend { repository.deleteAssetLocally(eq(assetId)) } throws expected
        val cleanup = AssetRepositoryDeleteMessageAssetCleanup(repository)

        val actual = assertFailsWith<CancellationException> { cleanup.deleteAssetLocally(assetId) }

        assertSame(expected, actual)
    }

    private companion object {
        const val assetId = "asset-id"
    }
}
