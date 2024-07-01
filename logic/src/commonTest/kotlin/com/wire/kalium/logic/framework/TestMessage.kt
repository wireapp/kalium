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

package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.BroadcastMessage
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.message.MessageSent
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

object TestMessage {
    const val TEST_MESSAGE_ID = "messageId"
    val TEST_DATE = Instant.parse("2023-02-01T12:34:50Z")
    val TEST_MESSAGE_SENT = MessageSent(TEST_DATE)
    val TEST_SENDER_USER_ID = TestUser.USER_ID
    val TEST_SENDER_CLIENT_ID = TestClient.CLIENT_ID
    val TEXT_CONTENT = MessageContent.Text("Ciao!")
    val ASSET_CONTENT = MessageContent.Asset(
        AssetContent(
            sizeInBytes = 0,
            name = null,
            mimeType = "",
            metadata = null,
            remoteData = AssetContent.RemoteData(
                otrKey = byteArrayOf(),
                sha256 = byteArrayOf(),
                assetId = "",
                assetToken = null,
                assetDomain = null,
                encryptionAlgorithm = null
            )
        )
    )
    val TEXT_MESSAGE = Message.Regular(
        id = TEST_MESSAGE_ID,
        content = TEXT_CONTENT,
        conversationId = TestConversation.ID,
        date = TEST_DATE,
        senderUserId = TEST_SENDER_USER_ID,
        senderClientId = TEST_SENDER_CLIENT_ID,
        status = Message.Status.Pending,
        editStatus = Message.EditStatus.NotEdited,
        isSelfMessage = false
    )

    val MISSED_CALL_MESSAGE = Message.System(
        id = TEST_MESSAGE_ID,
        content = MessageContent.MissedCall,
        conversationId = ConversationId("conv", "id"),
        date = TEST_DATE,
        senderUserId = TEST_SENDER_USER_ID,
        status = Message.Status.Pending,
        expirationData = null
    )

    fun assetMessage(assetId: String = TEST_MESSAGE_ID) = Message.Regular(
        id = TEST_MESSAGE_ID,
        content = ASSET_CONTENT,
        conversationId = ConversationId("conv", "id"),
        date = TEST_DATE,
        senderUserId = TEST_SENDER_USER_ID,
        senderClientId = TEST_SENDER_CLIENT_ID,
        status = Message.Status.Pending,
        editStatus = Message.EditStatus.NotEdited,
        isSelfMessage = false
    )

    val ENTITY = MessageEntity.Regular(
        TEST_MESSAGE_ID,
        TestConversation.ENTITY_ID,
        date = TEST_DATE,
        senderUserId = TestUser.ENTITY_ID,
        status = MessageEntity.Status.SENT,
        visibility = MessageEntity.Visibility.VISIBLE,
        content = MessageEntityContent.Text("Ciao!"),
        senderClientId = "clientId",
        editStatus = MessageEntity.EditStatus.NotEdited,
        senderName = "senderName",
        readCount = 0
    )

    val BROADCAST_MESSAGE = BroadcastMessage(
        id = TEST_MESSAGE_ID,
        content = MessageContent.Availability(UserAvailabilityStatus.AVAILABLE),
        date = TEST_DATE,
        senderUserId = TEST_SENDER_USER_ID,
        senderClientId = TEST_SENDER_CLIENT_ID,
        status = Message.Status.Pending,
        isSelfMessage = false
    )

    fun signalingMessage(
        content: MessageContent.Signaling
    ) = Message.Signaling(
        id = TEST_MESSAGE_ID,
        content = content,
        conversationId = TestConversation.ID,
        date = Clock.System.now(),
        senderUserId = TEST_SENDER_USER_ID,
        senderClientId = TEST_SENDER_CLIENT_ID,
        status = Message.Status.Sent,
        isSelfMessage = false,
        expirationData = null
    )
}
