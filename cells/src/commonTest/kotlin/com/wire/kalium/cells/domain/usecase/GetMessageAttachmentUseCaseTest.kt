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

import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageAttachment
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetMessageAttachmentUseCaseTest {

    @Test
    fun `given valid assetId when repository returns attachment then returns Right`() = runTest {
        // Given
        val assetId = "assetId"
        val messageAttachment = AssetContent(
            0L,
            "name",
            "image/jpg",
            AssetContent.AssetMetadata.Image(100, 100),
            AssetContent.RemoteData(
                otrKey = ByteArray(0),
                sha256 = ByteArray(16),
                assetId = "asset-id",
                assetToken = "==some-asset-token",
                assetDomain = "some-asset-domain.com",
                encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM
            ),
            AssetContent.LocalData(
                assetDataPath = "local_asset_path"
            ),
        )
        val (_, useCase) = Arrangement()
            .withRepositoryReturning(Either.Right(messageAttachment))
            .arrange()

        // When
        val result = useCase.invoke(assetId)

        // Then
        assertTrue(result is Either.Right)
        assertEquals(messageAttachment, result.value)
    }

    @Test
    fun `given valid assetId when repository returns failure then returns Left`() = runTest {
        // Given
        val assetId = "assetId"
        val (_, useCase) = Arrangement()
            .withRepositoryReturning(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        // When
        val result = useCase.invoke(assetId)

        // Then
        assertTrue(result is Either.Left)
        assertEquals(StorageFailure.DataNotFound, result.value)
    }

    private class Arrangement {

        val cellAttachmentsRepository = mock(CellAttachmentsRepository::class)

        suspend fun withRepositoryReturning(result: Either<StorageFailure, MessageAttachment>) = apply {
            coEvery {
                cellAttachmentsRepository.getAttachment(any())
            }.returns(result)
        }

        fun arrange() = this to GetMessageAttachmentUseCaseImpl(
            cellAttachmentsRepository = cellAttachmentsRepository
        )
    }
}
