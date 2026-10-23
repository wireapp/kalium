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

/**
 * Whether content received as a targeted and/or transient MLS message may be processed.
 *
 * Targeted and transient messages have weaker security guarantees than messages to the whole conversation, so
 * content is only accepted through them once a feature explicitly allows it here. Everything else is rejected.
 */
@Suppress("UNUSED_PARAMETER") // Per-content decisions are added as features need them.
internal fun isAcceptedDelivery(content: MessageContent.FromProto, targeted: Boolean, transient: Boolean): Boolean =
    !targeted && !transient

/** @see isAcceptedDelivery */
fun Message.Sendable.isAcceptedDelivery(): Boolean = isAcceptedDelivery(content, targeted, transient)

/** @see isAcceptedDelivery */
fun MessageUnpackResult.ApplicationMessage.isAcceptedDelivery(): Boolean =
    isAcceptedDelivery(content.messageContent, targeted, transient)
