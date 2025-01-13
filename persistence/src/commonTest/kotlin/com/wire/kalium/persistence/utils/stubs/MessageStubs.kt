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

package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.ButtonEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.draft.MessageDraftEntity
import kotlinx.datetime.Instant
import kotlin.random.Random

@Suppress("LongParameterList")
fun newRegularMessageEntity(
    id: String = "testMessage",
    content: MessageEntityContent.Regular = MessageEntityContent.Text("Test Text"),
    conversationId: QualifiedIDEntity = QualifiedIDEntity("convId", "convDomain"),
    senderUserId: QualifiedIDEntity = QualifiedIDEntity("senderId", "senderDomain"),
    senderClientId: String = "senderClientId",
    status: MessageEntity.Status = MessageEntity.Status.PENDING,
    editStatus: MessageEntity.EditStatus = MessageEntity.EditStatus.NotEdited,
    date: Instant = Instant.parse("2022-03-30T15:36:00.000Z"),
    visibility: MessageEntity.Visibility = MessageEntity.Visibility.VISIBLE,
    senderName: String = "senderName",
    expectsReadConfirmation: Boolean = false,
    expireAfterMs: Long? = null,
    selfDeletionEndDate: Instant? = null,
    sender: UserDetailsEntity? = null
) = MessageEntity.Regular(
    id = id,
    content = content,
    conversationId = conversationId,
    date = date,
    senderUserId = senderUserId,
    senderClientId = senderClientId,
    status = status,
    editStatus = editStatus,
    visibility = visibility,
    senderName = senderName,
    expectsReadConfirmation = expectsReadConfirmation,
    readCount = 0,
    expireAfterMs = expireAfterMs,
    selfDeletionEndDate = selfDeletionEndDate,
    sender = sender
)

@Suppress("LongParameterList")
fun newSystemMessageEntity(
    id: String = "testMessage",
    content: MessageEntityContent.System = MessageEntityContent.MemberChange(
        listOf(UserIDEntity("value", "domain")),
        MessageEntity.MemberChangeType.REMOVED
    ),
    conversationId: QualifiedIDEntity = QualifiedIDEntity("convId", "convDomain"),
    senderUserId: QualifiedIDEntity = QualifiedIDEntity("senderId", "senderDomain"),
    status: MessageEntity.Status = MessageEntity.Status.PENDING,
    date: Instant = Instant.parse("2022-03-30T15:36:00.000Z"),
    visibility: MessageEntity.Visibility = MessageEntity.Visibility.VISIBLE
) = MessageEntity.System(
    id = id,
    content = content,
    conversationId = conversationId,
    date = date,
    senderUserId = senderUserId,
    status = status,
    visibility = visibility,
    senderName = "senderName",
    expireAfterMs = null,
    selfDeletionEndDate = null,
    readCount = 0
)

fun newDraftMessageEntity(
    conversationId: QualifiedIDEntity = QualifiedIDEntity("convId", "convDomain"),
    text: String = "draft text",
    editMessageId: String? = null,
    quotedMessageId: String? = null,
    selectedMentionList: List<MessageEntity.Mention> = emptyList()
) = MessageDraftEntity(conversationId, text, editMessageId, quotedMessageId, selectedMentionList)

fun allMessageEntities(
    conversationId: QualifiedIDEntity = QualifiedIDEntity("convId", "convDomain"),
    senderUserId: QualifiedIDEntity,
): List<MessageEntity> {
    return listOf(
        newRegularMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage1",
            content = MessageEntityContent.Text(
                "@John @John",
                linkPreview = listOf(
                    MessageEntity.LinkPreview(
                        "https://www.wire.com",
                        0,
                        "https://www.wire.com",
                        "Wire",
                        "Wire is the most secure collaboration platform",
                    )
                ),
                mentions = listOf(
                    MessageEntity.Mention(0, 4, QualifiedIDEntity("senderId", "senderDomain")),
                    MessageEntity.Mention(6, 10, QualifiedIDEntity("senderId", "senderDomain"))
                ),
                quotedMessageId = "testMessage2",
                isQuoteVerified = true,
                quotedMessage = null
            )
        ),

        newRegularMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage2",
            content = MessageEntityContent.Asset(
                1000,
                assetName = "test name",
                assetMimeType = "image/png",
                assetOtrKey = byteArrayOf(1),
                assetSha256Key = byteArrayOf(1),
                assetId = "assetId",
                assetToken = "",
                assetDomain = "convDomain",
                assetEncryptionAlgorithm = "",
                assetWidth = null,
                assetHeight = 0,
            ),
        ),
        newRegularMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage3",
            content = MessageEntityContent.Knock(false)
        ),
        newRegularMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage4",
            content = MessageEntityContent.Location(
                latitude = 42.0f,
                longitude = -42.0f,
                name = "someSecretLocation",
                zoom = 20
            )
        ),
        newRegularMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage5",
            content = MessageEntityContent.Unknown(typeName = null, Random.nextBytes(1000))
        ),
        newRegularMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage6",
            content = MessageEntityContent.FailedDecryption(
                null,
                333,
                false,
                QualifiedIDEntity("senderId", "senderDomain"),
                "someClient"
            )
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage7",
            content = MessageEntityContent.MLSWrongEpochWarning
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage8",
            content = MessageEntityContent.MemberChange(
                listOf(UserIDEntity("value", "domain")),
                MessageEntity.MemberChangeType.REMOVED
            )
        ),
        newRegularMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage9",
            content = MessageEntityContent.RestrictedAsset("", 0, "name")
        ),
        newRegularMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage10",
            content = MessageEntityContent.Composite(
                MessageEntityContent.Text("text"),
                listOf(
                    ButtonEntity("text1", "id1", false),
                    ButtonEntity("tex2", "id2", false),
                    ButtonEntity("tex3", "id3", false),
                    ButtonEntity("tex4", "id4", false)
                )
            )
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage11",
            content = MessageEntityContent.MissedCall
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage12",
            content = MessageEntityContent.CryptoSessionReset
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage13",
            content = MessageEntityContent.ConversationRenamed("newName")
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage14",
            content = MessageEntityContent.TeamMemberRemoved("someUser")
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage15",
            content = MessageEntityContent.NewConversationReceiptMode(true)
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage16",
            content = MessageEntityContent.ConversationReceiptModeChanged(false)
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage17",
            content = MessageEntityContent.ConversationMessageTimerChanged(6000)
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage18",
            content = MessageEntityContent.ConversationProtocolChanged(ConversationEntity.Protocol.MIXED)
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage19",
            content = MessageEntityContent.ConversationProtocolChangedDuringACall
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage20",
            content = MessageEntityContent.HistoryLostProtocolChanged
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage21",
            content = MessageEntityContent.HistoryLost
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage22",
            content = MessageEntityContent.ConversationCreated
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage23",
            content = MessageEntityContent.ConversationDegradedMLS
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage24",
            content = MessageEntityContent.ConversationVerifiedMLS
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage25",
            content = MessageEntityContent.ConversationDegradedProteus
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage26",
            content = MessageEntityContent.ConversationVerifiedProteus
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage27",
            content = MessageEntityContent.ConversationStartedUnverifiedWarning
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage28",
            content = MessageEntityContent.Federation(
                listOf("otherDomain"),
                MessageEntity.FederationType.DELETE
            )
        ),
        newSystemMessageEntity(
            conversationId = conversationId,
            senderUserId = senderUserId,
            id = "testMessage29",
            content = MessageEntityContent.LegalHold(
                listOf(QualifiedIDEntity("otherId", "otherDomain")), MessageEntity.LegalHoldType.ENABLED_FOR_MEMBERS
            )
        ),
    )
}
