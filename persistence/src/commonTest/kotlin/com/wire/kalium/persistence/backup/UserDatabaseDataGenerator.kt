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

@file:Suppress("StringTemplate")

package com.wire.kalium.persistence.backup

import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.TeamEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.asset.AssetEntity
import com.wire.kalium.persistence.dao.call.CallEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlin.random.Random

class UserDatabaseDataGenerator(
    private val userDatabaseBuilder: UserDatabaseBuilder,
    private val databasePrefix: String
) {
    companion object {
        val DEFAULT_DATE = Instant.parse("2000-01-01T12:00:00.000Z")
        val DEFAULT_RECEIPT_MODE = ConversationEntity.ReceiptMode.DISABLED
    }

    private var generatedUsersCount = 0
    private var generatedConversationsCount = 0
    private var generatedMessagesCount = 0
    private var generatedCallsCount = 0
    private var generatedTeamCount = 0
    private var generatedAssetsCount = 0

    @Suppress("StringTemplate")
    private suspend fun generateAndInsertRegularMessages(
        amount: Int,
        conversationIDEntity: ConversationIDEntity,
    ): List<MessageEntity> {
        val messagePrefix = "regular${databasePrefix}Message${generatedMessagesCount}"
        val messages = mutableListOf<MessageEntity>()

        for (index in generatedMessagesCount + 1..amount) {
            val senderUser = generateUser()
            userDatabaseBuilder.userDAO.upsertUser(senderUser)

            val visibility = MessageEntity.Visibility.values()[index % MessageEntity.Visibility.values().size]

            val sanitizedVisibility =
                if (visibility == MessageEntity.Visibility.DELETED)
                    MessageEntity.Visibility.values()[(index + 1) % MessageEntity.Visibility.values().size]
                else visibility

            messages.add(
                MessageEntity.Regular(
                    id = "${messagePrefix}messageId",
                    content = MessageEntityContent.Text("${messagePrefix}Text$index"),
                    conversationId = conversationIDEntity,
                    date = DEFAULT_DATE,
                    senderUserId = senderUser.id,
                    senderClientId = "${messagePrefix}senderClientId",
                    status = MessageEntity.Status.values()[index % MessageEntity.Status.values().size],
                    editStatus = if (index % 2 == 0)
                        MessageEntity.EditStatus.NotEdited else
                        MessageEntity.EditStatus.Edited(DEFAULT_DATE),
                    visibility = sanitizedVisibility,
                    senderName = "$messagePrefix SenderName",
                    readCount = 0
                )
            )

            generatedMessagesCount += 1
        }

        return messages
    }

    @Suppress("StringTemplate")
    private suspend fun generateAssetMessages(
        amount: Int,
        conversationIDEntity: ConversationIDEntity
    ): List<MessageEntity> {
        val messagePrefix = "Asset${databasePrefix}Message${generatedAssetsCount}"
        val messages = mutableListOf<MessageEntity>()

        for (index in generatedAssetsCount + 1..amount) {
            val senderUser = generateUser()
            userDatabaseBuilder.userDAO.upsertUser(senderUser)

            val visibility = MessageEntity.Visibility.values()[index % MessageEntity.Visibility.values().size]

            val sanitizedVisibility =
                if (visibility == MessageEntity.Visibility.DELETED)
                    MessageEntity.Visibility.values()[(index + 1) % MessageEntity.Visibility.values().size]
                else visibility

            messages.add(
                MessageEntity.Regular(
                    id = "${messagePrefix}messageId",
                    content = generateMessageAssetContent(),
                    conversationId = conversationIDEntity,
                    date = DEFAULT_DATE,
                    senderUserId = senderUser.id,
                    senderClientId = "${messagePrefix}senderClientId",
                    status = MessageEntity.Status.values()[index % MessageEntity.Status.values().size],
                    editStatus = if (index % 2 == 0)
                        MessageEntity.EditStatus.NotEdited else
                        MessageEntity.EditStatus.Edited(DEFAULT_DATE),
                    visibility = sanitizedVisibility,
                    senderName = "$messagePrefix SenderName",
                    readCount = 0
                )
            )

            generatedAssetsCount += 1
        }

        return messages
    }

    @Suppress("StringTemplate")
    private fun generateMessageAssetContent(): MessageEntityContent.Asset {
        val messageAssetContentPrefix = "${databasePrefix}MessageAssetContent${generatedAssetsCount}"

        return MessageEntityContent.Asset(
            assetSizeInBytes = 256,
            assetName = "${messageAssetContentPrefix}Name",
            assetMimeType = "MP4",
            assetOtrKey = byteArrayOf(1),
            assetSha256Key = byteArrayOf(1),
            assetId = "${messageAssetContentPrefix}AssetId",
            assetToken = "${messageAssetContentPrefix}AssetToken",
            assetDomain = "${messageAssetContentPrefix}AssetDomain",
            assetEncryptionAlgorithm = "",
            assetWidth = 256,
            assetHeight = 256,
            assetDurationMs = 10,
            assetNormalizedLoudness = byteArrayOf(1)
        )
    }

    @Suppress("StringTemplate")
    private fun generateUser(): UserEntity {
        val userPrefix = "${databasePrefix}User${generatedUsersCount}"

        generatedUsersCount += 1

        return UserEntity(
            id = UserIDEntity("${userPrefix}Value", "${userPrefix}Domain"),
            availabilityStatus = UserAvailabilityStatusEntity.values()[generatedUsersCount % UserAvailabilityStatusEntity.values().size],
            userType = UserTypeEntity.values()[generatedUsersCount % UserTypeEntity.values().size],
            deleted = generatedUsersCount % 2 == 0,
            name = "${userPrefix}Name",
            handle = "${userPrefix}Handle",
            email = "${userPrefix}Email",
            phone = "${userPrefix}Phone",
            accentId = 0,
            team = null,
            connectionStatus = ConnectionEntity.State.values()[generatedUsersCount % ConnectionEntity.State.values().size],
            previewAssetId = null,
            completeAssetId = null,
            botService = null,
            hasIncompleteMetadata = false,
            expiresAt = null,
            defederated = false,
            supportedProtocols = null,
            activeOneOnOneConversationId = null
        )
    }

    @Suppress("StringTemplate")
    private suspend fun generateAndInsertSystemMessages(
        amount: Int,
        conversationIDEntity: ConversationIDEntity
    ): List<MessageEntity> {
        val messagePrefix = "system${databasePrefix}Message${generatedMessagesCount}"
        val messages = mutableListOf<MessageEntity>()

        for (index in generatedMessagesCount + 1..amount) {
            val senderUser = generateUser()
            userDatabaseBuilder.userDAO.upsertUser(senderUser)

            val visibility = MessageEntity.Visibility.values()[index % MessageEntity.Visibility.values().size]

            val sanitizedVisibility =
                if (visibility == MessageEntity.Visibility.DELETED)
                    MessageEntity.Visibility.values()[(index + 1) % MessageEntity.Visibility.values().size]
                else visibility

            messages.add(
                MessageEntity.System(
                    id = "${messagePrefix}messageId",
                    content = MessageEntityContent.MissedCall,
                    conversationId = conversationIDEntity,
                    date = DEFAULT_DATE,
                    senderUserId = senderUser.id,
                    status = MessageEntity.Status.values()[index % MessageEntity.Status.values().size],
                    visibility = sanitizedVisibility,
                    senderName = "$messagePrefix SenderName",
                    expireAfterMs = null,
                    selfDeletionEndDate = null,
                    readCount = 0
                )
            )

            generatedMessagesCount += 1
        }

        return messages
    }

    @Suppress("StringTemplate")
    suspend fun generateAndInsertConversations(
        conversationAmount: Int,
        messagePerConversation: Int,
        messageType: MessageType
    ): List<ConversationEntity> {
        val conversationPrefix = "${databasePrefix}Conversation${generatedConversationsCount}"

        for (index in generatedConversationsCount + 1..conversationAmount) {
            val conversationId = QualifiedIDEntity(
                "${conversationPrefix}Value$index",
                "${conversationPrefix}Domain$index"
            )

            val conversationType = ConversationEntity.Type.values()[index % ConversationEntity.Type.values().size]

            val sanitizedConversationType =
                if (conversationType == ConversationEntity.Type.CONNECTION_PENDING)
                    ConversationEntity.Type.values()[(index + 1) % ConversationEntity.Type.values().size]
                else conversationType

            userDatabaseBuilder.conversationDAO.insertConversation(
                ConversationEntity(
                    id = conversationId,
                    name = "${conversationPrefix}Name$index",
                    type = sanitizedConversationType,
                    teamId = null,
                    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
                    mutedStatus = ConversationEntity.MutedStatus.values()[index % ConversationEntity.MutedStatus.values().size],
                    mutedTime = 0,
                    removedBy = null,
                    creatorId = "${conversationPrefix}CreatorId$index",
                    lastNotificationDate = DEFAULT_DATE,
                    lastModifiedDate = DEFAULT_DATE,
                    lastReadDate = DEFAULT_DATE,
                    access = listOf(ConversationEntity.Access.values()[index % ConversationEntity.Access.values().size]),
                    accessRole = listOf(ConversationEntity.AccessRole.values()[index % ConversationEntity.AccessRole.values().size]),
                    receiptMode = DEFAULT_RECEIPT_MODE,
                    messageTimer = null,
                    userMessageTimer = null,
                    archived = false,
                    archivedInstant = null,
                    mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                    proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                    legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
                    wireCell = null,
                )
            )

            generatedConversationsCount += 1

            userDatabaseBuilder.messageDAO.insertOrIgnoreMessages(
                if (messageType == MessageType.Regular) {
                    generateAndInsertRegularMessages(messagePerConversation, conversationId)
                } else {
                    generateAndInsertSystemMessages(messagePerConversation, conversationId)
                }
            )
        }

        return userDatabaseBuilder.conversationDAO.getAllConversations().first()
    }

    suspend fun generateAndInsertConversationWithLastReadDate(
        lastReadDate: Instant,
        conversationId: ConversationIDEntity? = null,
        index: Int = Random.nextInt(0, 5)
    ): ConversationEntity {
        val randomID = Random.nextBytes(16).decodeToString()
        val type = if (index % 2 == 0) ConversationEntity.Type.ONE_ON_ONE else ConversationEntity.Type.GROUP
        val conversation = ConversationEntity(
            id = conversationId ?: ConversationIDEntity(randomID, "some-domain-$index"),
            name = "name-$index",
            type = type,
            teamId = null,
            protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
            mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
            mutedTime = 0,
            removedBy = null,
            creatorId = "creatorId$index",
            lastNotificationDate = DEFAULT_DATE,
            lastModifiedDate = DEFAULT_DATE,
            lastReadDate = lastReadDate,
            access = listOf(ConversationEntity.Access.values()[index % ConversationEntity.Access.values().size]),
            accessRole = listOf(ConversationEntity.AccessRole.values()[index % ConversationEntity.AccessRole.values().size]),
            receiptMode = ConversationEntity.ReceiptMode.DISABLED,
            messageTimer = null,
            userMessageTimer = null,
            archived = false,
            archivedInstant = null,
            mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
            wireCell = null,
        )
        userDatabaseBuilder.conversationDAO.insertConversation(conversation)
        return conversation
    }

    @Suppress("StringTemplate")
    private suspend fun generateAndInsertCall(conversationId: QualifiedIDEntity): CallEntity {
        val callPrefix = "${databasePrefix}Call${generatedCallsCount}"

        val userEntity = generateUser()
        userDatabaseBuilder.userDAO.upsertUser(userEntity)

        val conversationType = ConversationEntity.Type.values()[generatedCallsCount % ConversationEntity.Type.values().size]
        val type = CallEntity.Type.values()[generatedCallsCount % CallEntity.Type.values().size]

        val sanitizedConversationType =
            if (conversationType == ConversationEntity.Type.CONNECTION_PENDING)
                ConversationEntity.Type.values()[(generatedCallsCount + 1) % ConversationEntity.Type.values().size]
            else conversationType

        val callEntity = CallEntity(
            conversationId = conversationId,
            id = "${callPrefix}Id${generatedCallsCount}",
            status = CallEntity.Status.values()[generatedCallsCount % CallEntity.Status.values().size],
            callerId = userEntity.id.value,
            conversationType = sanitizedConversationType,
            type = type
        )

        userDatabaseBuilder.callDAO.insertCall(callEntity)

        generatedCallsCount += 1

        return callEntity
    }

    @Suppress("StringTemplate")
    suspend fun generateAndInsertConversationsWithCall(conversationAmount: Int): List<Pair<ConversationEntity, CallEntity>> {
        val groupConversationPrefix = "${databasePrefix}CallConversation${generatedConversationsCount}"

        val conversationWithAssociatedCall = mutableListOf<Pair<ConversationEntity, CallEntity>>()

        for (index in generatedConversationsCount + 1..conversationAmount) {
            val conversationId = QualifiedIDEntity(
                "${groupConversationPrefix}Value$index",
                "${groupConversationPrefix}Domain$index"
            )

            val conversationEntity = ConversationEntity(
                id = conversationId,
                name = "${groupConversationPrefix}Name$index",
                type = ConversationEntity.Type.GROUP,
                teamId = null,
                protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
                mutedStatus = ConversationEntity.MutedStatus.values()[index % ConversationEntity.MutedStatus.values().size],
                mutedTime = 0,
                removedBy = null,
                creatorId = "${groupConversationPrefix}CreatorId$index",
                lastNotificationDate = DEFAULT_DATE,
                lastModifiedDate = DEFAULT_DATE,
                lastReadDate = DEFAULT_DATE,
                access = listOf(ConversationEntity.Access.values()[index % ConversationEntity.Access.values().size]),
                accessRole = listOf(ConversationEntity.AccessRole.values()[index % ConversationEntity.AccessRole.values().size]),
                receiptMode = DEFAULT_RECEIPT_MODE,
                messageTimer = null,
                userMessageTimer = null,
                archived = false,
                archivedInstant = null,
                mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
                wireCell = null,
            )

            userDatabaseBuilder.conversationDAO.insertConversation(conversationEntity)

            generatedConversationsCount += 1

            val generatedCall = generateAndInsertCall(conversationId)

            conversationWithAssociatedCall.add(Pair(conversationEntity, generatedCall))
        }

        return conversationWithAssociatedCall
    }

    @Suppress("StringTemplate")
    fun generateTeams(teamAmount: Int): List<TeamEntity> {
        val teamPrefix = "${databasePrefix}Team${generatedTeamCount}"
        val teams = mutableListOf<TeamEntity>()

        for (index in generatedTeamCount + 1..teamAmount) {

            TeamEntity(
                id = "${teamPrefix}Id$index",
                name = "${teamPrefix}name$index",
                icon = "${teamPrefix}icon$index"
            )

            generatedTeamCount += 1
        }

        return teams
    }

    @Suppress("StringTemplate")
    suspend fun generateAndInsertGroupConversations(
        conversationAmount: Int,
        membersPerGroup: Int
    ): List<ConversationEntity> {
        val groupConversationPrefix = "${databasePrefix}GroupConversation${generatedConversationsCount}"

        for (index in generatedConversationsCount + 1..conversationAmount) {
            val conversationId = QualifiedIDEntity(
                "${groupConversationPrefix}Value$index",
                "${groupConversationPrefix}Domain$index"
            )

            userDatabaseBuilder.conversationDAO.insertConversation(
                ConversationEntity(
                    id = conversationId,
                    name = "${groupConversationPrefix}Name$index",
                    type = ConversationEntity.Type.GROUP,
                    teamId = null,
                    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
                    mutedStatus = ConversationEntity.MutedStatus.values()[index % ConversationEntity.MutedStatus.values().size],
                    mutedTime = 0,
                    removedBy = null,
                    creatorId = "${groupConversationPrefix}CreatorId$index",
                    lastNotificationDate = DEFAULT_DATE,
                    lastModifiedDate = DEFAULT_DATE,
                    lastReadDate = DEFAULT_DATE,
                    access = listOf(ConversationEntity.Access.values()[index % ConversationEntity.Access.values().size]),
                    accessRole = listOf(ConversationEntity.AccessRole.values()[index % ConversationEntity.AccessRole.values().size]),
                    receiptMode = DEFAULT_RECEIPT_MODE,
                    messageTimer = null,
                    userMessageTimer = null,
                    archived = false,
                    archivedInstant = null,
                    mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                    proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                    legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
                    wireCell = null,
                )
            )

            generateAndInsertConversationMembers(conversationId, membersPerGroup)

            generatedConversationsCount += 1
        }

        return userDatabaseBuilder.conversationDAO.getAllConversations().first()
    }

    @Suppress("StringTemplate")
    suspend fun generateAndInsertGroupConversations(
        conversationAmount: Int,
        membersGenerate: (ConversationIDEntity) -> List<MemberEntity>
    ): List<ConversationEntity> {
        val groupConversationPrefix = "${databasePrefix}GroupConversation${generatedConversationsCount}"

        for (index in generatedConversationsCount + 1..conversationAmount) {
            val conversationId = QualifiedIDEntity(
                "${groupConversationPrefix}Value$index",
                "${groupConversationPrefix}Domain$index"
            )

            userDatabaseBuilder.conversationDAO.insertConversation(
                ConversationEntity(
                    id = conversationId,
                    name = "${groupConversationPrefix}Name$index",
                    type = ConversationEntity.Type.GROUP,
                    teamId = null,
                    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
                    mutedStatus = ConversationEntity.MutedStatus.values()[index % ConversationEntity.MutedStatus.values().size],
                    mutedTime = 0,
                    removedBy = null,
                    creatorId = "${groupConversationPrefix}CreatorId$index",
                    lastNotificationDate = DEFAULT_DATE,
                    lastModifiedDate = DEFAULT_DATE,
                    lastReadDate = DEFAULT_DATE,
                    access = listOf(ConversationEntity.Access.values()[index % ConversationEntity.Access.values().size]),
                    accessRole = listOf(ConversationEntity.AccessRole.values()[index % ConversationEntity.AccessRole.values().size]),
                    receiptMode = DEFAULT_RECEIPT_MODE,
                    messageTimer = null,
                    userMessageTimer = null,
                    archived = false,
                    archivedInstant = null,
                    mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                    proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                    legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
                    wireCell = null,
                )
            )

            val members = membersGenerate(conversationId)

            members.forEach { member ->
                userDatabaseBuilder.memberDAO.insertMember(member, conversationId)
            }

            generatedConversationsCount += 1
        }

        return userDatabaseBuilder.conversationDAO.getAllConversations().first()
    }

    private suspend fun generateAndInsertConversationMembers(conversationId: QualifiedIDEntity, membersPerGroup: Int) {
        for (index in generatedUsersCount + 1..membersPerGroup) {
            val userEntity = generateUser()
            userDatabaseBuilder.memberDAO.insertMember(MemberEntity(userEntity.id, MemberEntity.Role.Member), conversationId)
        }
    }

    fun generateMembers(amount: Int): List<MemberEntity> {
        val members = mutableListOf<MemberEntity>()

        for (index in generatedUsersCount + 1..amount) {
            val userEntity = generateUser()

            members.add(MemberEntity(userEntity.id, MemberEntity.Role.Member))
        }

        return members
    }

    suspend fun generateAndInsertUsers(amount: Int): List<UserDetailsEntity> {
        for (index in generatedUsersCount + 1..amount) {
            val user = generateUser()

            userDatabaseBuilder.userDAO.upsertUser(user)
        }

        return userDatabaseBuilder.userDAO.getAllUsersDetails().first()
    }

    @Suppress("StringTemplate")
    suspend fun generateAndInsertAssets(amount: Int): MutableList<AssetEntity> {
        val assetPrefix = "${databasePrefix}Asset${generatedAssetsCount}"

        val generatedAssets = mutableListOf<AssetEntity>()

        for (index in generatedAssetsCount + 1..amount) {

            val generatedAsset = AssetEntity(
                key = "${assetPrefix}Key${index}",
                domain = "${assetPrefix}Domain${index}",
                dataPath = "${assetPrefix}DataPath${index}",
                dataSize = 256,
                downloadedDate = null
            )

            userDatabaseBuilder.assetDAO.insertAsset(generatedAsset)

            generatedAssetsCount += 1

            generatedAssets.add(generatedAsset)
        }

        return generatedAssets
    }

    @Suppress("StringTemplate")
    suspend fun generateAndInsertMessageAssetContent(
        conversationAmount: Int,
        assetAmountPerConversation: Int
    ): List<ConversationEntity> {
        val conversationPrefix = "${databasePrefix}Conversation${generatedConversationsCount}"

        for (index in generatedConversationsCount + 1..conversationAmount) {
            val conversationId = QualifiedIDEntity(
                "${conversationPrefix}Value$index",
                "${conversationPrefix}Domain$index"
            )

            val conversationType = ConversationEntity.Type.values()[index % ConversationEntity.Type.values().size]

            val sanitizedConversationType =
                if (conversationType == ConversationEntity.Type.CONNECTION_PENDING)
                    ConversationEntity.Type.values()[(index + 1) % ConversationEntity.Type.values().size]
                else conversationType

            userDatabaseBuilder.conversationDAO.insertConversation(
                ConversationEntity(
                    id = conversationId,
                    name = "${conversationPrefix}Name$index",
                    type = sanitizedConversationType,
                    teamId = null,
                    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
                    mutedStatus = ConversationEntity.MutedStatus.values()[index % ConversationEntity.MutedStatus.values().size],
                    mutedTime = 0,
                    removedBy = null,
                    creatorId = "${conversationPrefix}CreatorId$index",
                    lastNotificationDate = DEFAULT_DATE,
                    lastModifiedDate = DEFAULT_DATE,
                    lastReadDate = DEFAULT_DATE,
                    access = listOf(ConversationEntity.Access.values()[index % ConversationEntity.Access.values().size]),
                    accessRole = listOf(ConversationEntity.AccessRole.values()[index % ConversationEntity.AccessRole.values().size]),
                    receiptMode = DEFAULT_RECEIPT_MODE,
                    messageTimer = null,
                    userMessageTimer = null,
                    archived = false,
                    archivedInstant = null,
                    mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                    proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
                    legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
                    wireCell = null,
                )
            )

            generatedConversationsCount += 1

            userDatabaseBuilder.messageDAO.insertOrIgnoreMessages(
                generateAssetMessages(
                    amount = assetAmountPerConversation,
                    conversationIDEntity = conversationId
                )
            )
        }

        return userDatabaseBuilder.conversationDAO.getAllConversations().first()
    }

}

enum class MessageType {
    Regular
}
