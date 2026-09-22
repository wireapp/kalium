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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.user.UserId

data class ApplicationMessage(
    val message: ByteArray,
    val senderID: UserId,
    val senderClientID: ClientId
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ApplicationMessage

        if (!message.contentEquals(other.message)) return false
        if (senderID != other.senderID) return false
        if (senderClientID != other.senderClientID) return false

        return true
    }

    override fun hashCode(): Int {
        var result = message.contentHashCode()
        result = 31 * result + senderID.hashCode()
        result = 31 * result + senderClientID.hashCode()
        return result
    }
}

sealed interface DecryptedMessageBundle {
    val groupID: GroupID
    val identity: WireIdentity?

    data class Text(
        override val groupID: GroupID,
        val applicationMessage: ApplicationMessage,
        override val identity: WireIdentity?
    ) : DecryptedMessageBundle

    data class Commit(
        override val groupID: GroupID,
        val isActive: Boolean,
        override val identity: WireIdentity?
    ) : DecryptedMessageBundle

    data class Proposal(
        override val groupID: GroupID,
        val commitDelay: Long?,
        override val identity: WireIdentity?
    ) : DecryptedMessageBundle
}
