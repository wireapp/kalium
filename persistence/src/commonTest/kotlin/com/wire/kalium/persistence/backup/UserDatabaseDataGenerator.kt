package com.wire.kalium.persistence.backup

import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.ConversationViewEntity
import com.wire.kalium.persistence.dao.Member
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.TeamEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.asset.AssetEntity
import com.wire.kalium.persistence.dao.call.CallEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.first

class UserDatabaseDataGenerator(
    private val userDatabaseBuilder: UserDatabaseBuilder,
    private val databasePrefix: String
) {
    companion object {
        const val DEFAULT_DATE_STRING = "2000-01-01T12:00:00.000Z"
    }

    private var generatedUsersCount = 0
    private var generatedConversationsCount = 0
    private var generatedMessagesCount = 0
    private var generatedCallsCount = 0
    private var generatedTeamCount = 0
    private var generatedAssetsCount = 0

    private suspend fun generateAndInsertRegularMessages(
        amount: Int,
        conversationIDEntity: ConversationIDEntity,
    ): List<MessageEntity> {
        val messagePrefix = "regular${databasePrefix}Message${generatedMessagesCount}"
        val messages = mutableListOf<MessageEntity>()

        for (index in generatedMessagesCount + 1..amount) {
            val senderUser = generateUser()
            userDatabaseBuilder.userDAO.insertUser(senderUser)

            messages.add(
                MessageEntity.Regular(
                    id = "${messagePrefix}messageId",
                    content = MessageEntityContent.Text("${messagePrefix}Text$index"),
                    conversationId = conversationIDEntity,
                    date = DEFAULT_DATE_STRING,
                    senderUserId = senderUser.id,
                    senderClientId = "${messagePrefix}senderClientId",
                    status = MessageEntity.Status.values()[index % MessageEntity.Status.values().size],
                    editStatus = if (index % 2 == 0)
                        MessageEntity.EditStatus.NotEdited else
                        MessageEntity.EditStatus.Edited(DEFAULT_DATE_STRING),
                    visibility = MessageEntity.Visibility.values()[index % MessageEntity.Visibility.values().size]
                )
            )

            generatedMessagesCount += 1
        }

        return messages
    }

    private suspend fun generateAssetMessages(
        amount: Int,
        conversationIDEntity: ConversationIDEntity,
        assetUploadStatus: MessageEntity.UploadStatus,
        assetDownloadStatus: MessageEntity.DownloadStatus
    ): List<MessageEntity> {
        val messagePrefix = "Asset${databasePrefix}Message${generatedAssetsCount}"
        val messages = mutableListOf<MessageEntity>()

        for (index in generatedAssetsCount + 1..amount) {
            val senderUser = generateUser()
            userDatabaseBuilder.userDAO.insertUser(senderUser)

            messages.add(
                MessageEntity.Regular(
                    id = "${messagePrefix}messageId",
                    content = generateMessageAssetContent(assetUploadStatus, assetDownloadStatus),
                    conversationId = conversationIDEntity,
                    date = DEFAULT_DATE_STRING,
                    senderUserId = senderUser.id,
                    senderClientId = "${messagePrefix}senderClientId",
                    status = MessageEntity.Status.values()[index % MessageEntity.Status.values().size],
                    editStatus = if (index % 2 == 0)
                        MessageEntity.EditStatus.NotEdited else
                        MessageEntity.EditStatus.Edited(DEFAULT_DATE_STRING),
                    visibility = MessageEntity.Visibility.values()[index % MessageEntity.Visibility.values().size]
                )
            )

            generatedAssetsCount += 1
        }

        return messages
    }

    private fun generateMessageAssetContent(
        assetUploadStatus: MessageEntity.UploadStatus,
        assetDownloadStatus: MessageEntity.DownloadStatus
    ): MessageEntityContent.Regular {
        val messageAssetContentPrefix = "${databasePrefix}MessageAssetContent${generatedAssetsCount}"

        return MessageEntityContent.Asset(
            assetSizeInBytes = 256,
            assetName = "${messageAssetContentPrefix}Name",
            assetMimeType = "MP4",
            assetUploadStatus = assetUploadStatus,
            assetDownloadStatus = assetDownloadStatus,
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
            botService = null
        )
    }

    private suspend fun generateAndInsertSystemMessages(
        amount: Int,
        conversationIDEntity: ConversationIDEntity
    ): List<MessageEntity> {
        val messagePrefix = "system${databasePrefix}Message${generatedMessagesCount}"
        val messages = mutableListOf<MessageEntity>()

        for (index in generatedMessagesCount + 1..amount) {
            val senderUser = generateUser()
            userDatabaseBuilder.userDAO.insertUser(senderUser)

            messages.add(
                MessageEntity.System(
                    id = "${messagePrefix}messageId",
                    content = MessageEntityContent.MissedCall,
                    conversationId = conversationIDEntity,
                    date = DEFAULT_DATE_STRING,
                    senderUserId = senderUser.id,
                    status = MessageEntity.Status.values()[index % MessageEntity.Status.values().size],
                    visibility = MessageEntity.Visibility.values()[index % MessageEntity.Visibility.values().size]
                )
            )

            generatedMessagesCount += 1
        }

        return messages
    }

    suspend fun generateAndInsertConversations(
        conversationAmount: Int,
        messagePerConversation: Int,
        messageType: MessageType
    ): List<ConversationViewEntity> {
        val conversationPrefix = "${databasePrefix}Conversation${generatedConversationsCount}"

        for (index in generatedConversationsCount + 1..conversationAmount) {
            val conversationId = QualifiedIDEntity(
                "${conversationPrefix}Value$index",
                "${conversationPrefix}Domain$index"
            )

            val conversationType = ConversationEntity.Type.values()[index % ConversationEntity.Type.values().size]

            val invalidatedConversationType =
                if (conversationType == ConversationEntity.Type.CONNECTION_PENDING)
                    ConversationEntity.Type.values()[(index + 1) % ConversationEntity.Type.values().size]
                else conversationType

            userDatabaseBuilder.conversationDAO.insertConversation(
                ConversationEntity(
                    id = conversationId,
                    name = "${conversationPrefix}Name$index",
                    type = invalidatedConversationType,
                    teamId = null,
                    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
                    mutedStatus = ConversationEntity.MutedStatus.values()[index % ConversationEntity.MutedStatus.values().size],
                    mutedTime = 0,
                    removedBy = null,
                    creatorId = "${conversationPrefix}CreatorId$index",
                    lastNotificationDate = DEFAULT_DATE_STRING,
                    lastModifiedDate = DEFAULT_DATE_STRING,
                    lastReadDate = DEFAULT_DATE_STRING,
                    access = listOf(ConversationEntity.Access.values()[index % ConversationEntity.Access.values().size]),
                    accessRole = listOf(ConversationEntity.AccessRole.values()[index % ConversationEntity.AccessRole.values().size])
                )
            )

            generatedConversationsCount += 1

            userDatabaseBuilder.messageDAO.insertMessages(
                if (messageType == MessageType.Regular) {
                    generateAndInsertRegularMessages(messagePerConversation, conversationId)
                } else {
                    generateAndInsertSystemMessages(messagePerConversation, conversationId)
                }
            )
        }

        return userDatabaseBuilder.conversationDAO.getAllConversations().first()
    }

    private suspend fun generateAndInsertCall(conversationId: QualifiedIDEntity): CallEntity {
        val callPrefix = "${databasePrefix}Call${generatedCallsCount}"

        val userEntity = generateUser()
        userDatabaseBuilder.userDAO.insertUser(userEntity)

        val conversationType = ConversationEntity.Type.values()[generatedCallsCount % ConversationEntity.Type.values().size]

        val invalidatedConversationType =
            if (conversationType == ConversationEntity.Type.CONNECTION_PENDING)
                ConversationEntity.Type.values()[(generatedCallsCount + 1) % ConversationEntity.Type.values().size]
            else conversationType

        val callEntity = CallEntity(
            conversationId = conversationId,
            id = "${callPrefix}Id${generatedCallsCount}",
            status = CallEntity.Status.values()[generatedCallsCount % CallEntity.Status.values().size],
            callerId = userEntity.id.value,
            conversationType = invalidatedConversationType
        )

        userDatabaseBuilder.callDAO.insertCall(callEntity)

        generatedCallsCount += 1

        return callEntity
    }

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
                lastNotificationDate = DEFAULT_DATE_STRING,
                lastModifiedDate = DEFAULT_DATE_STRING,
                lastReadDate = DEFAULT_DATE_STRING,
                access = listOf(ConversationEntity.Access.values()[index % ConversationEntity.Access.values().size]),
                accessRole = listOf(ConversationEntity.AccessRole.values()[index % ConversationEntity.AccessRole.values().size])
            )

            userDatabaseBuilder.conversationDAO.insertConversation(conversationEntity)

            generatedConversationsCount += 1

            val generatedCall = generateAndInsertCall(conversationId)

            conversationWithAssociatedCall.add(Pair(conversationEntity, generatedCall))
        }

        return conversationWithAssociatedCall
    }

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

    suspend fun generateAndInsertGroupConversations(
        conversationAmount: Int,
        membersPerGroup: Int
    ): List<ConversationViewEntity> {
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
                    lastNotificationDate = DEFAULT_DATE_STRING,
                    lastModifiedDate = DEFAULT_DATE_STRING,
                    lastReadDate = DEFAULT_DATE_STRING,
                    access = listOf(ConversationEntity.Access.values()[index % ConversationEntity.Access.values().size]),
                    accessRole = listOf(ConversationEntity.AccessRole.values()[index % ConversationEntity.AccessRole.values().size])
                )
            )

            generateAndInsertConversationMembers(conversationId, membersPerGroup)

            generatedConversationsCount += 1
        }

        return userDatabaseBuilder.conversationDAO.getAllConversations().first()
    }

    suspend fun generateAndInsertGroupConversations(
        conversationAmount: Int,
        membersGenerate: (ConversationIDEntity) -> List<Member>
    ): List<ConversationViewEntity> {
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
                    lastNotificationDate = DEFAULT_DATE_STRING,
                    lastModifiedDate = DEFAULT_DATE_STRING,
                    lastReadDate = DEFAULT_DATE_STRING,
                    access = listOf(ConversationEntity.Access.values()[index % ConversationEntity.Access.values().size]),
                    accessRole = listOf(ConversationEntity.AccessRole.values()[index % ConversationEntity.AccessRole.values().size])
                )
            )

            val members = membersGenerate(conversationId)

            members.forEach { member ->
                userDatabaseBuilder.conversationDAO.insertMember(member, conversationId)
            }

            generatedConversationsCount += 1
        }

        return userDatabaseBuilder.conversationDAO.getAllConversations().first()
    }

    private suspend fun generateAndInsertConversationMembers(conversationId: QualifiedIDEntity, membersPerGroup: Int) {
        for (index in generatedUsersCount + 1..membersPerGroup) {
            val userEntity = generateUser()
            userDatabaseBuilder.conversationDAO.insertMember(Member(userEntity.id, Member.Role.Member), conversationId)
        }
    }

    fun generateMembers(amount: Int): List<Member> {
        val members = mutableListOf<Member>()

        for (index in generatedUsersCount + 1..amount) {
            val userEntity = generateUser()

            members.add(Member(userEntity.id, Member.Role.Member))
        }

        return members
    }

    suspend fun generateAndInsertUsers(amount: Int): List<UserEntity> {
        for (index in generatedUsersCount + 1..amount) {
            val user = generateUser()

            userDatabaseBuilder.userDAO.insertUser(user)
        }

        return userDatabaseBuilder.userDAO.getAllUsers().first()
    }

    suspend fun generateAndInsertAssets(amount: Int): MutableList<AssetEntity> {
        val assetPrefix = "${databasePrefix}Asset${generatedAssetsCount}"

        val generatedAssets = mutableListOf<AssetEntity>()

        for (index in generatedAssetsCount + 1..amount) {

            val generatedAsset = AssetEntity(
                key = "${assetPrefix}Key${index}",
                domain = "${assetPrefix}Domain${index}",
                dataPath = "${assetPrefix}DataPath${index}",
                dataSize = 256,
                assetToken = null,
                downloadedDate = null
            )

            userDatabaseBuilder.assetDAO.insertAsset(generatedAsset)

            generatedAssetsCount += 1

            generatedAssets.add(generatedAsset)
        }

        return generatedAssets
    }

    suspend fun generateAndInsertMessageAssetContent(
        conversationAmount: Int,
        assetAmountPerConversation: Int,
        assetUploadStatus: MessageEntity.UploadStatus,
        assetDownloadStatus: MessageEntity.DownloadStatus
    ): List<ConversationViewEntity> {
        val conversationPrefix = "${databasePrefix}Conversation${generatedConversationsCount}"

        for (index in generatedConversationsCount + 1..conversationAmount) {
            val conversationId = QualifiedIDEntity(
                "${conversationPrefix}Value$index",
                "${conversationPrefix}Domain$index"
            )

            val conversationType = ConversationEntity.Type.values()[index % ConversationEntity.Type.values().size]

            val invalidatedConversationType =
                if (conversationType == ConversationEntity.Type.CONNECTION_PENDING)
                    ConversationEntity.Type.values()[(index + 1) % ConversationEntity.Type.values().size]
                else conversationType

            userDatabaseBuilder.conversationDAO.insertConversation(
                ConversationEntity(
                    id = conversationId,
                    name = "${conversationPrefix}Name$index",
                    type = invalidatedConversationType,
                    teamId = null,
                    protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
                    mutedStatus = ConversationEntity.MutedStatus.values()[index % ConversationEntity.MutedStatus.values().size],
                    mutedTime = 0,
                    removedBy = null,
                    creatorId = "${conversationPrefix}CreatorId$index",
                    lastNotificationDate = DEFAULT_DATE_STRING,
                    lastModifiedDate = DEFAULT_DATE_STRING,
                    lastReadDate = DEFAULT_DATE_STRING,
                    access = listOf(ConversationEntity.Access.values()[index % ConversationEntity.Access.values().size]),
                    accessRole = listOf(ConversationEntity.AccessRole.values()[index % ConversationEntity.AccessRole.values().size])
                )
            )

            generatedConversationsCount += 1

            userDatabaseBuilder.messageDAO.insertMessages(
                generateAssetMessages(
                    amount = assetAmountPerConversation,
                    conversationIDEntity = conversationId,
                    assetUploadStatus = assetUploadStatus,
                    assetDownloadStatus = assetDownloadStatus
                )
            )
        }

        return userDatabaseBuilder.conversationDAO.getAllConversations().first()
    }

}

enum class MessageType {
    Regular
}
