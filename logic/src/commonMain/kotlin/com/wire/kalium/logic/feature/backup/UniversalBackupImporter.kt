/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.backup.MPBackup
import com.wire.backup.data.BackupData
import com.wire.backup.data.BackupMessageContent
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.toLongMilliseconds
import com.wire.backup.ingest.BackupImportResult
import com.wire.backup.ingest.MPBackupImporter
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.Failure
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.Failure.IncompatibleBackup
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.MigrationDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.DeliveryStatusEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.reaction.ReactionsEntity
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.datetime.Instant
import okio.Path
import okio.buffer

class UniversalBackupImporter(
    private val userId: UserId,
    private val kaliumFileSystem: KaliumFileSystem,
    private val migrationDAO: MigrationDAO,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(userId)
) {

    suspend fun isMultiplatformBackup(extractedBackupRootPath: Path): Boolean {
        return kaliumFileSystem.listDirectories(extractedBackupRootPath)
            .any { it.name == MPBackup.ZIP_ENTRY_DATA }
    }

    suspend fun import(extractedBackupRootPath: Path): Either<Failure, Unit> {
        val byteArray =
            kaliumFileSystem.source(extractedBackupRootPath / MPBackup.ZIP_ENTRY_DATA).buffer().readByteArray()
        return when (val importResult = MPBackupImporter(userId.domain).import(byteArray)) {
            BackupImportResult.ParsingFailure -> Either.Left(IncompatibleBackup("backupMetadata: Parsing failure"))
            is BackupImportResult.Success -> {
                importData(importResult.backupData)
                Either.Right(Unit)
            }
        }
    }

    fun BackupQualifiedId.toEntity() = QualifiedIDEntity(
        id, domain
    )

    private suspend fun importData(backupData: BackupData) {
        backupData.messages.map {
            MessageEntity.Regular(
                it.id,
                it.conversationId.toEntity(),
                Instant.fromEpochMilliseconds(it.creationDate.toLongMilliseconds()),
                it.senderUserId.toEntity(),
                MessageEntity.Status.SENT,
                MessageEntity.Visibility.VISIBLE,
                content = when (val content = it.content) {
                    is BackupMessageContent.Asset -> MessageEntityContent.Text(
                        "Asset message was here" // TODO: Add rest
                    )

                    is BackupMessageContent.Text -> MessageEntityContent.Text(
                        content.text, // TODO: Add rest
                    )
                },
                isSelfMessage = it.senderUserId == userId.toBackup(),
                readCount = 0L,
                expireAfterMs = null,
                selfDeletionEndDate = null,
                sender = null,
                senderName = null,
                senderClientId = it.senderClientId,
                editStatus = MessageEntity.EditStatus.NotEdited,
                reactions = ReactionsEntity.EMPTY,
                expectsReadConfirmation = false,
                deliveryStatus = DeliveryStatusEntity.CompleteDelivery
            )
        }.also {
            migrationDAO.insertMessages(it)
        }
    }
}
