/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.receipt.ReceiptType

/**
 * Whether content received as a targeted and/or transient MLS message may be processed.
 *
 * Targeted and transient messages have weaker security guarantees than messages to the whole conversation, so
 * content is only accepted through them once a feature explicitly allows it here. Everything else is rejected.
 *
 * Allowed:
 * - Read receipts sent as persistent targeted messages, since they are only sent to the sender of the read messages.
 */
internal fun isAcceptedDelivery(content: MessageContent.FromProto, targeted: Boolean, transient: Boolean): Boolean =
    when {
        !targeted && !transient -> true
        targeted && !transient -> content.isReadReceipt()
        else -> false
    }

private fun MessageContent.FromProto.isReadReceipt(): Boolean =
    this is MessageContent.Receipt && type == ReceiptType.READ

/** @see isAcceptedDelivery */
fun Message.Sendable.isAcceptedDelivery(): Boolean = isAcceptedDelivery(content, targeted, transient)

/** @see isAcceptedDelivery */
fun MessageUnpackResult.ApplicationMessage.isAcceptedDelivery(): Boolean =
    isAcceptedDelivery(content.messageContent, targeted, transient)
