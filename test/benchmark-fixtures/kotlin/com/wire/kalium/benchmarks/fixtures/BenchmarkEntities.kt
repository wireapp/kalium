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
@file:Suppress("MagicNumber")

package com.wire.kalium.benchmarks.fixtures

import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlin.time.Instant

internal object BenchmarkEntities {
    val conversationId = QualifiedIDEntity("conversationId", "wire.com")
    val conversation: ConversationEntity = newConversationEntity(conversationId.value)
    val userOne: UserEntity = newUserEntity(QualifiedIDEntity("userEntity1", "wire.com"))
    val userTwo: UserEntity = newUserEntity(QualifiedIDEntity("userEntity2", "wire.com"))

    val selfUser: UserEntity = newUserEntity(QualifiedIDEntity("benchmark-self", "benchmark.wire.com"))
    val users: List<UserEntity> = listOf(selfUser, userOne, userTwo)

    fun messages(count: Int, idOffset: Int = 0): List<MessageEntity.Regular> {
        val users = listOf(userOne, userTwo)
        return buildList(count) {
            repeat(count) { index ->
                add(
                    MessageEntity.Regular(
                        id = (idOffset + index).toString(),
                        conversationId = conversationId,
                        date = Instant.fromEpochMilliseconds((idOffset + index).toLong()),
                        senderUserId = users[index % users.size].id,
                        status = MessageEntity.Status.SENT,
                        visibility = MessageEntity.Visibility.VISIBLE,
                        content = content(index),
                        senderClientId = "benchmark-client",
                        editStatus = MessageEntity.EditStatus.NotEdited,
                        senderName = "senderName",
                        readCount = 0
                    )
                )
            }
        }
    }

    private fun content(index: Int): MessageEntityContent.Regular = when (index % 20) {
        0 -> MessageEntityContent.Unknown(typeName = null, ByteArray(1000) { 1 })
        in 1..17 -> MessageEntityContent.Text("Message $index: fixed text for database benchmarks.")
        18 -> MessageEntityContent.Asset(
            1000,
            assetName = "test name",
            assetMimeType = "MP4",
            assetOtrKey = byteArrayOf(1),
            assetSha256Key = byteArrayOf(1),
            assetId = "assetId",
            assetToken = "",
            assetDomain = "domain",
            assetEncryptionAlgorithm = "",
            assetWidth = 111,
            assetHeight = 111,
            assetDurationMs = 10,
            assetNormalizedLoudness = byteArrayOf(1),
        )
        else -> MessageEntityContent.Knock(false)
    }

    private fun newUserEntity(qualifiedID: QualifiedIDEntity, id: String = "test") = UserEntity(
        id = qualifiedID,
        name = "user$id",
        handle = "handle$id",
        email = "email$id",
        phone = "phone$id",
        accentId = 1,
        team = "team",
        ConnectionEntity.State.ACCEPTED,
        null,
        null,
        UserAvailabilityStatusEntity.NONE,
        UserTypeEntity.STANDARD,
        botService = null,
        deleted = false,
        hasIncompleteMetadata = false,
        expiresAt = null,
        defederated = false,
        supportedProtocols = setOf(SupportedProtocolEntity.PROTEUS),
        activeOneOnOneConversationId = null
    )

    private fun newConversationEntity(id: String = "test") = ConversationEntity(
        id = QualifiedIDEntity(id, "wire.com"),
        name = "conversation1",
        type = ConversationEntity.Type.GROUP,
        teamId = "teamID",
        protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
        creatorId = "someValue",
        lastNotificationDate = null,
        lastModifiedDate = Instant.fromEpochMilliseconds(0),
        lastReadDate = Instant.fromEpochMilliseconds(0),
        access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
        receiptMode = ConversationEntity.ReceiptMode.DISABLED,
        messageTimer = null,
        userMessageTimer = null,
        archived = false,
        archivedInstant = null,
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
        channelAccess = null,
        channelAddPermission = null,
        wireCell = null,
        historySharingRetentionSeconds = 0
    )
}
