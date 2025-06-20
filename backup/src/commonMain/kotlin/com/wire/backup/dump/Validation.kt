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
package com.wire.backup.dump

import com.wire.backup.data.BackupConversation
import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupMessageContent
import com.wire.backup.data.BackupUser

internal fun BackupUser.validate() {
    if (id.id.isEmpty()) error("User ID cannot be empty")
}

internal fun BackupConversation.validate() {
    if (id.id.isEmpty()) error("Conversation ID cannot be empty")
}

internal fun BackupMessage.validate() {
    if (id.isEmpty()) error("Message ID cannot be empty")
    if (conversationId.id.isEmpty()) error("Conversation ID cannot be empty")
    if (senderUserId.id.isEmpty()) error("Sender ID cannot be empty")

    content.validate()
}

internal fun BackupMessageContent.validate() {
   when (this) {
       is BackupMessageContent.Text -> {
           if (text.isEmpty()) error("Text content cannot be empty")
       }
       is BackupMessageContent.Asset -> {
           if (assetId.isEmpty()) error("Asset ID cannot be empty")
           if (otrKey.isEmpty()) error("Asset OTR key cannot be empty")
           if (sha256.isEmpty()) error("Asset SHA256 cannot be empty")
       }
       is BackupMessageContent.Location -> {
           if (latitude == 0f || longitude == 0f) {
               error("Location content must have valid latitude and longitude")
           }
       }
   }
}
