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
import com.wire.backup.logger.BackupLogger

/**
 * Validates the given [BackupUser] and its content.
 *
 * @throws IllegalStateException if mandatory validation fails.
 */
internal fun BackupExporterDelegate.validate(user: BackupUser): Boolean =
    if (user.id.id.isEmpty()) {
        logger?.log("User ID cannot be empty")
        false
    } else {
        true
    }

/**
 * Validates the given [BackupConversation] and its content.
 *
 * @throws IllegalStateException if mandatory validation fails.
 */
internal fun BackupExporterDelegate.validate(conversation: BackupConversation): Boolean =
    if (conversation.id.id.isEmpty()) {
        logger?.log("Conversation ID cannot be empty")
        false
    } else {
        true
    }

/**
 * Validates the given [BackupMessage] and its content.
 *
 * @return `true` if the message is valid, `false` otherwise.
 * @throws IllegalStateException if mandatory validation fails.
 */
internal fun BackupExporterDelegate.validate(message: BackupMessage): Boolean = with(message) {
    if (id.isEmpty()) {
        logger?.log("Backup: Message ID cannot be empty")
        return@with false
    }
    if (conversationId.id.isEmpty()) {
        logger?.log("Backup: Conversation ID cannot be empty")
        return@with false
    }
    if (senderUserId.id.isEmpty()) {
        logger?.log("Backup: Sender ID cannot be empty")
        return@with false
    }

    return@with validate(content)
}

/**
 * Validates the given [BackupMessageContent] and its content.
 *
 * @return `true` if the content is valid, `false` otherwise.
 * @throws IllegalStateException if mandatory validation fails.
 */
private fun BackupExporterDelegate.validate(content: BackupMessageContent): Boolean = with(content) {
    when (this) {
        is BackupMessageContent.Text -> {
            if (text.isEmpty()) {
                logger?.log("Backup: Text content cannot be empty")
                return@with false
            }
        }

        is BackupMessageContent.Asset -> {
            if (assetId.isEmpty()) {
                logger?.log("Backup: Asset ID cannot be empty")
                return@with false
            }
            if (otrKey.isEmpty()) {
                logger?.log("Backup: Asset OTR key cannot be empty")
                return@with false
            }
            if (sha256.isEmpty()) {
                logger?.log("Backup: Asset SHA256 cannot be empty")
                return@with false
            }
        }

        is BackupMessageContent.Location -> {
            if (latitude == 0f || longitude == 0f) {
                logger?.log("Backup: Location content must have valid latitude and longitude")
                return@with false
            }
        }
    }
    return true
}
