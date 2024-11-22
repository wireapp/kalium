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
package com.wire.backup.dump

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.BackupUser
import com.wire.backup.data.toProtoModel
import com.wire.backup.ingest.MPBackupMapper
import com.wire.kalium.protobuf.backup.BackupData
import com.wire.kalium.protobuf.backup.BackupInfo
import kotlinx.datetime.Clock
import pbandk.encodeToByteArray
import kotlin.experimental.ExperimentalObjCName
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.js.JsExport
import kotlin.native.ObjCName
import kotlin.native.ShouldRefineInSwift

/**
 * Entity able to serialize [BackupData] entities, like [BackupMessage], [BackupConversation], [BackupUser]
 * into a cross-platform [BackupData] format.
 */
@OptIn(ExperimentalObjCName::class, ExperimentalObjCRefinement::class)
@JsExport
abstract class CommonMPBackupExporter(
    private val selfUserId: BackupQualifiedId
) {
    private val mapper = MPBackupMapper()
    private val allUsers = mutableListOf<BackupUser>()
    private val allConversations = mutableListOf<BackupConversation>()
    private val allMessages = mutableListOf<BackupMessage>()

    // TODO: Replace `ObjCName` with `JsName` in the future and flip it around.
    //       Unfortunately the IDE doesn't understand this right now and
    //       keeps complaining if making the other way around
    @ObjCName("add")
    fun addUser(user: BackupUser) {
        allUsers.add(user)
    }

    @ObjCName("add")
    fun addConversation(conversation: BackupConversation) {
        allConversations.add(conversation)
    }

    @ObjCName("add")
    fun addMessage(message: BackupMessage) {
        allMessages.add(message)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @ShouldRefineInSwift // Hidden in Swift
    fun serialize(): ByteArray {
        val backupData = BackupData(
            BackupInfo(
                platform = "Common",
                version = "1.0",
                userId = selfUserId.toProtoModel(),
                creationTime = Clock.System.now().toEpochMilliseconds(),
                clientId = "lol"
            ),
            allConversations.map {
                mapper.mapConversationToProtobuf(it)
            },
            allMessages.map {
                mapper.mapMessageToProtobuf(it)
            },
            allUsers.map {
                mapper.mapUserToProtobuf(it)
            },
        )
        return backupData.encodeToByteArray().also {
            println("XPlatform Backup POC. Exported data bytes: ${it.toHexString()}")
        }
    }
}

expect class MPBackupExporter : CommonMPBackupExporter
