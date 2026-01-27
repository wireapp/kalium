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

package com.wire.kalium.persistence.dao.backup

/**
 * Combined event types for the remote backup change log.
 * Code ranges indicate level:
 * - 1-9: Message-level events (require message_nonce)
 * - 10-99: Sub-entity events at message level (reactions, etc.)
 * - 100+: Conversation-level events (message_nonce = "")
 */
@Suppress("MagicNumber")
enum class ChangeLogEventType(val code: Int) {
    // Message-level (require message_nonce)
    MESSAGE_UPSERT(1), // Message created or edited
    MESSAGE_DELETE(2), // Message deleted

    // Sub-entity events at message level
    REACTIONS_SYNC(300), // Any reaction changed - sync all reactions for this message
    READ_RECIEPT_SYBC(301), // Any read receipt changed - sync all read receipts for this message

    // Conversation-level (message_nonce = "")
    CONVERSATION_DELETE(500), // Conversation deleted
    CONVERSATION_CLEAR(501); // Conversation history cleared

    companion object {
        fun fromCode(code: Int): ChangeLogEventType? = entries.find { it.code == code }

        fun fromCodeOrThrow(code: Int): ChangeLogEventType =
            fromCode(code) ?: throw IllegalArgumentException("Unknown ChangeLogEventType code: $code")
    }
}
