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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.persistence.dao.message.MessageEntity

enum class ReceivedMessageDeletion { NONE, REMOVE, TOMBSTONE }

/** The app also accepts any receiver's expiry signal for a self-authored ephemeral message. */
fun Message.receivedDeletion(senderUserId: UserId, selfUserId: UserId): ReceivedMessageDeletion = receivedDeletion(
    ephemeral = (this as? Message.Regular)?.expirationData != null,
    selfAuthored = this.senderUserId == selfUserId,
    authorized = senderUserId == this.senderUserId || senderUserId == selfUserId,
    deleted = (this as? Message.Standalone)?.visibility == Message.Visibility.DELETED,
)

fun MessageEntity.receivedDeletion(senderUserId: UserId, selfUserId: UserId): ReceivedMessageDeletion = receivedDeletion(
    ephemeral = this is MessageEntity.Regular && expireAfterMs != null,
    selfAuthored = this.senderUserId == selfUserId.toDao(),
    authorized = senderUserId.toDao() == this.senderUserId || senderUserId == selfUserId,
    deleted = visibility == MessageEntity.Visibility.DELETED,
)

private fun receivedDeletion(ephemeral: Boolean, selfAuthored: Boolean, authorized: Boolean, deleted: Boolean) = when {
    ephemeral && (selfAuthored || authorized) -> ReceivedMessageDeletion.REMOVE
    authorized && !deleted -> ReceivedMessageDeletion.TOMBSTONE
    else -> ReceivedMessageDeletion.NONE
}
