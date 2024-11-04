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
package com.wire.backup.data

import kotlin.js.JsExport

@JsExport
class BackupData(
    val metadata: BackupMetadata,
    val users: Array<BackupUser>,
    val conversations: Array<BackupConversation>,
    val messages: Array<BackupMessage>
)

@JsExport
data class BackupQualifiedId(
    val id: String,
    val domain: String,
)

@JsExport
data class BackupUser(
    val id: BackupQualifiedId,
    val name: String,
    val handle: String,
)

@JsExport
data class BackupConversation(
    val id: BackupQualifiedId,
    val name: String,
)

@JsExport
data class BackupMessage(
    val id: String,
    val conversationId: BackupQualifiedId,
    val senderUserId: BackupQualifiedId,
    val senderClientId: String,
    val creationDate: BackupDateTime,
    val content: BackupMessageContent
)

expect class BackupDateTime

expect fun BackupDateTime(timestamp: Long): BackupDateTime
internal expect fun BackupDateTime.toLongMilliseconds(): Long

@JsExport
sealed class BackupMessageContent {
    data class Text(val text: String) : BackupMessageContent()
    data class Asset(val TODO: String) : BackupMessageContent()
}
