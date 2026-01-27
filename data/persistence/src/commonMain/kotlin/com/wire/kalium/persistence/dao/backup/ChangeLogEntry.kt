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

import com.wire.kalium.persistence.dao.QualifiedIDEntity

/**
 * Represents a single entry in the remote backup change log.
 *
 * @property conversationId The conversation where the change occurred
 * @property messageId The message identifier (empty for conversation-level events)
 * @property eventType The type of change that occurred
 * @property timestampMs The timestamp when the change was recorded (epoch milliseconds)
 */
data class ChangeLogEntry(
    val conversationId: QualifiedIDEntity,
    val messageId: String,
    val eventType: ChangeLogEventType,
    val timestampMs: Long
)
