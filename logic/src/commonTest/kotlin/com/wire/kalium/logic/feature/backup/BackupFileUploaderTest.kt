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

import com.wire.kalium.cells.domain.usecase.BackupCellFile
import com.wire.kalium.cells.domain.usecase.BackupCellFileUseCase
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BackupFileUploaderTest {

    @Test
    fun givenBackupFile_whenUploading_thenUploadsToResolvedBackupConversation() = runTest {
        val backupConversationId = ConversationId("conversation-id", "domain")
        val backupFile = BackupCellFile(
            uuid = "node-id",
            versionId = "version-id",
            path = "cell/user_123.wbu",
        )
        val backupCellFile = FakeBackupCellFileUseCase(uploadResult = Either.Right(backupFile))
        val uploader = BackupFileUploaderImpl(
            backupConversationResolver = FakeBackupConversationResolver(Either.Right(backupConversationId)),
            backupCellFile = backupCellFile,
        )

        val result = uploader.upload("backup.wbu".toPath(), "user_123.wbu")

        val uploadedAssetId = assertIs<Either.Right<UploadedAssetId>>(result).value
        assertEquals(backupConversationId, backupCellFile.uploadConversationId)
        assertEquals("backup.wbu".toPath(), backupCellFile.uploadLocalPath)
        assertEquals("user_123.wbu", backupCellFile.uploadFileName)
        assertEquals("node-id", uploadedAssetId.key)
        assertEquals("version-id", uploadedAssetId.domain)
        assertEquals("cell/user_123.wbu", uploadedAssetId.assetToken)
    }

    @Test
    fun givenBackupConversationCannotBeResolved_whenUploading_thenReturnsFailure() = runTest {
        val failure = StorageFailure.DataNotFound
        val uploader = BackupFileUploaderImpl(
            backupConversationResolver = FakeBackupConversationResolver(Either.Left(failure)),
            backupCellFile = FakeBackupCellFileUseCase(uploadResult = Either.Right(BackupCellFile("", "", ""))),
        )

        val result = uploader.upload("backup.wbu".toPath(), "user_123.wbu")

        assertEquals(failure, assertIs<Either.Left<*>>(result).value)
    }

    private class FakeBackupConversationResolver(
        private val result: Either<CoreFailure, ConversationId>,
    ) : BackupConversationResolver {
        override suspend fun getOrCreateBackupConversation(): Either<CoreFailure, ConversationId> = result
    }

    private class FakeBackupCellFileUseCase(
        private val uploadResult: Either<CoreFailure, BackupCellFile>,
    ) : BackupCellFileUseCase {
        var uploadConversationId: ConversationId? = null
        var uploadLocalPath: Path? = null
        var uploadFileName: String? = null

        override suspend fun upload(
            conversationId: ConversationId,
            localPath: Path,
            fileName: String,
        ): Either<CoreFailure, BackupCellFile> {
            uploadConversationId = conversationId
            uploadLocalPath = localPath
            uploadFileName = fileName
            return uploadResult
        }

        override suspend fun listMetadataFiles(conversationId: ConversationId): Either<CoreFailure, List<BackupCellFile>> =
            Either.Right(emptyList())

        override suspend fun download(remotePath: String, outputPath: Path): Either<CoreFailure, Unit> =
            Either.Right(Unit)
    }
}
