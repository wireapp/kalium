package com.wire.kalium.logic.data

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant

object MockConversation {
    private const val conversationValue = "valueConvo"
    private const val conversationDomain = "domainConvo"

    val ID = ConversationId(conversationValue, conversationDomain)
    val ENTITY_ID = QualifiedIDEntity(conversationValue, conversationDomain)

    fun id(suffix: Int = 0) = ConversationId("${conversationValue}_$suffix", conversationDomain)

    fun oneOnOne(protocolInfo: ProtocolInfo = ProtocolInfo.Proteus) = Conversation(
        id = ID.copy(value = "1O1 ID"),
        name = "ONE_ON_ONE Name",
        type = Conversation.Type.OneOnOne,
        teamId = TeamId("teamId"),
        protocol = protocolInfo,
        mutedStatus = MutedConversationStatus.AllAllowed,
        removedBy = null,
        lastNotificationDate = null,
        lastModifiedDate = null,
        lastReadDate = Instant.parse("2022-03-30T15:36:00.000Z"),
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
        creatorId = null,
        receiptMode = Conversation.ReceiptMode.DISABLED,
        messageTimer = null,
        userMessageTimer = null,
        archived = false,
        archivedDateTime = null,
        mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
    )

    fun self(protocolInfo: ProtocolInfo = ProtocolInfo.Proteus) = Conversation(
        id = ID.copy(value = "SELF ID"),
        name = "SELF Name",
        type = Conversation.Type.Self,
        teamId = TeamId("teamId"),
        protocol = protocolInfo,
        mutedStatus = MutedConversationStatus.AllAllowed,
        removedBy = null,
        lastNotificationDate = null,
        lastModifiedDate = null,
        lastReadDate = Instant.parse("2022-03-30T15:36:00.000Z"),
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
        creatorId = null,
        receiptMode = Conversation.ReceiptMode.DISABLED,
        messageTimer = null,
        userMessageTimer = null,
        archived = false,
        archivedDateTime = null,
        mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
    )

    fun group(id: ConversationId = ID, protocolInfo: ProtocolInfo = ProtocolInfo.Proteus) = Conversation(
        id = id,
        name = "GROUP Name",
        type = Conversation.Type.Group.Regular,
        teamId = TeamId("teamId"),
        protocol = protocolInfo,
        mutedStatus = MutedConversationStatus.AllAllowed,
        removedBy = null,
        lastNotificationDate = null,
        lastModifiedDate = "2022-03-30T15:36:00.000Z".toInstant(),
        lastReadDate = "2022-03-30T15:36:00.000Z".toInstant(),
        access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
        accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
        creatorId = "someValue",
        receiptMode = Conversation.ReceiptMode.DISABLED,
        messageTimer = null,
        userMessageTimer = null,
        archived = false,
        archivedDateTime = null,
        mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
    )

    fun channel(
        protocolInfo: ProtocolInfo = ProtocolInfo.Proteus
    ) = group(protocolInfo = protocolInfo).copy(type = Conversation.Type.Group.Channel)

    fun entity(
        id: ConversationIDEntity = ENTITY_ID,
    ) = ConversationEntity(
        id,
        "convo name",
        ConversationEntity.Type.SELF,
        "teamId",
        ConversationEntity.ProtocolInfo.Proteus,
        creatorId = "someValue",
        lastNotificationDate = null,
        lastModifiedDate = "2022-03-30T15:36:00.000Z".toInstant(),
        lastReadDate = "2022-03-30T15:36:00.000Z".toInstant(),
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
        isChannel = false,
        channelAccess = null,
        channelAddPermission = null,
        wireCell = null,
        historySharingRetentionSeconds = 0,
    )
}
