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
package com.wire.backup.ingest

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupData
import com.wire.backup.data.BackupDateTime
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupMessageContent
import com.wire.backup.data.BackupMetadata
import com.wire.backup.data.BackupUser
import com.wire.backup.data.toModel
import com.wire.kalium.protobuf.backup.ExportedMessage.Content
import kotlinx.datetime.toInstant
import pbandk.decodeFromByteArray
import kotlin.js.JsExport
import com.wire.kalium.protobuf.backup.BackupData as ProtoBackupData

@JsExport
class MPBackupImporter(val selfUserDomain: String) {

    @OptIn(ExperimentalStdlibApi::class)
    fun import(data: ByteArray): BackupImportResult = try {
        println("!!!BACKUP: ${data.toHexString()}")
        BackupImportResult.Success(
            ProtoBackupData.decodeFromByteArray(data).run {
                // TODO: Map all the stuff
                BackupData(
                    BackupMetadata(
                        info.platform,
                        info.version,
                        info.userId.toModel(),
                        BackupDateTime(info.creationTime),
                        info.clientId
                    ),
                    users.map { user ->
                        BackupUser(user.id.toModel(), user.name, user.handle)
                    }.toTypedArray(),
                    conversations.map { conversation ->
                        BackupConversation(conversation.id.toModel(), conversation.name)
                    }.toTypedArray(),
                    messages.map { message ->
                        val content = when (val proContent = message.content) {
                            is Content.Text -> {
                                BackupMessageContent.Text(proContent.value.content)
                            }

                            null -> TODO()
                        }
                        BackupMessage(
                            id = message.id,
                            conversationId = message.conversationId.toModel(),
                            senderUserId = message.senderUserId.toModel(),
                            senderClientId = message.senderClientId,
                            creationDate = BackupDateTime(message.timeIso),
                            content = content
                        )
                    }.toTypedArray()
                )
            }
        )
    } catch (e: Exception) {
        e.printStackTrace()
        println(e)
        BackupImportResult.ParsingFailure
    }
}
