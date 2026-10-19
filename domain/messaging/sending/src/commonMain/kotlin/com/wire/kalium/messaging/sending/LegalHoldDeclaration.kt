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

package com.wire.kalium.messaging.sending

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.message.Message

/** The legal-hold status a message declares: only regular messages declare the stored status. */
internal fun declaredLegalHoldStatus(
    stored: Either<StorageFailure, Conversation.LegalHoldStatus>,
    message: Message.Sendable,
): Conversation.LegalHoldStatus = when (stored) {
    is Either.Right -> stored.value.takeIf { message is Message.Regular } ?: Conversation.LegalHoldStatus.UNKNOWN
    is Either.Left -> Conversation.LegalHoldStatus.UNKNOWN
}
