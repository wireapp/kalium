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
package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.asset.UploadedAssetId
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupFileUploaderTest {

    @Test
    fun givenBackupFile_whenUploading_thenPassesBackupMetadataToAssetRepository() = runTest {
        val filePath = "backup.wbu".toPath()
        val fileName = "user_123.wbu"
        val uploadedAssetId = UploadedAssetId("asset-key", "asset-domain")
        val (arrangement, uploader) = Arrangement()
            .withFileSize(filePath, 123L)
            .withUploadResult(Either.Right(uploadedAssetId))
            .arrange()

        val result = uploader.upload(filePath, fileName)

        assertEquals(Either.Right(uploadedAssetId), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.uploadAndPersistPublicAsset(
                mimeType = eq("application/octet-stream"),
                assetDataPath = eq(filePath),
                assetDataSize = eq(123L),
                conversationId = eq(null),
                filename = eq(fileName),
                filetype = eq("wire-mp-backup"),
            )
        }
    }

    @Test
    fun givenAssetUploadFails_whenUploading_thenReturnsFailure() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (_, uploader) = Arrangement()
            .withFileSize("backup.wbu".toPath(), 123L)
            .withUploadResult(Either.Left(failure))
            .arrange()

        val result = uploader.upload("backup.wbu".toPath(), "user_123.wbu")

        assertEquals(Either.Left(failure), result)
    }

    private class Arrangement {
        val assetRepository = mock<AssetRepository>()
        private val kaliumFileSystem = mock<KaliumFileSystem>()

        fun withFileSize(filePath: okio.Path, size: Long) = apply {
            every { kaliumFileSystem.size(eq(filePath)) } returns size
        }

        suspend fun withUploadResult(result: Either<CoreFailure, UploadedAssetId>) = apply {
            everySuspend {
                assetRepository.uploadAndPersistPublicAsset(any(), any(), any(), any(), any(), any())
            } returns result
        }

        fun arrange(): Pair<Arrangement, BackupFileUploader> =
            this to BackupFileUploaderImpl(assetRepository, kaliumFileSystem)
    }
}
