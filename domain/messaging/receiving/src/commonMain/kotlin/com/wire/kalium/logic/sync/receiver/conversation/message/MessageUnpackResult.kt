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

package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.datetime.Instant

/**
 * Result of passing an [Event] through [MLSMessageUnpacker] or [ProteusMessageUnpacker].
 */
@InternalKaliumApi
public sealed interface MessageUnpackResult {

    /**
     * The [Event] was successfully processed by the unpacker, and didn't result in
     * any [ApplicationMessage], only protocol-specific signaling/handshake.
     */
    public data object HandshakeMessage : MessageUnpackResult

    /**
     * The processed [Event] was successfully processed and resulted in a [ApplicationMessage].
     * This message should be handled according to its content by the caller.
     */
    public data class ApplicationMessage public constructor(
        public val conversationId: ConversationId,
        public val instant: Instant,
        public val senderUserId: UserId,
        public val senderClientId: ClientId,
        public val content: ProtoContent.Readable
    ) : MessageUnpackResult

}
