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
package com.wire.backup.export

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.BackupUser
import com.wire.backup.data.toProtoModel
import com.wire.kalium.protobuf.backup.BackupData
import com.wire.kalium.protobuf.backup.BackupInfo
import com.wire.kalium.protobuf.backup.ExportUser
import com.wire.kalium.protobuf.backup.ExportedConversation
import com.wire.kalium.protobuf.backup.ExportedMessage
import com.wire.kalium.protobuf.backup.ExportedText
import kotlinx.datetime.Clock
import pbandk.encodeToByteArray
import kotlin.js.JsExport

@JsExport
class MPBackupExporter(
    val selfUserId: BackupQualifiedId
) {
    private val allUsers = mutableListOf<BackupUser>()
    private val allConversations = mutableListOf<BackupConversation>()
    private val allMessages = mutableListOf<BackupMessage>()

    fun addUser(user: BackupUser) {
        allUsers.add(user)
    }

    fun addConversation(conversation: BackupConversation) {
        allConversations.add(conversation)
    }

    fun addMessage(message: BackupMessage) {
        allMessages.add(message)
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun serialize(): ByteArray {
        val backupData = BackupData(
            BackupInfo(
                platform = "Common",
                version = "1.0",
                userId = selfUserId.toProtoModel(),
                creationTime = Clock.System.now().toString(),
                clientId = "lol"
            ),
            allConversations.map { ExportedConversation(it.id.toProtoModel(), it.name) },
            allMessages.map {
                ExportedMessage(
                    id = it.id,
                    timeIso = Clock.System.now().toString(),
                    senderUserId = it.senderUserId.toProtoModel(),
                    senderClientId = it.senderClientId,
                    conversationId = it.conversationId.toProtoModel(),
                    content = when (val content = it.content) {
                        is com.wire.backup.data.BackupMessageContent.Asset -> ExportedMessage.Content.Text(ExportedText("FAKE ASSET")) // TODO:
                        is com.wire.backup.data.BackupMessageContent.Text -> ExportedMessage.Content.Text(ExportedText(content.text))
                    }
                )
            },
            allUsers.map {
                ExportUser(
                    id = it.id.toProtoModel(),
                    name = it.name,
                    handle = it.handle
                )
            },
        )
        return backupData.encodeToByteArray().also {
            println("!!!BACKUP: ${it.toHexString()}")
        }
    }
}
