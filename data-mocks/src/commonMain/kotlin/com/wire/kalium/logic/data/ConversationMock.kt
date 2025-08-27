package com.wire.kalium.logic.data

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant

object ConversationMock {
    private const val conversationValue = "valueConvo"
    private const val conversationDomain = "domainConvo"

    val ID = ConversationId(conversationValue, conversationDomain)
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

    fun group(protocolInfo: ProtocolInfo = ProtocolInfo.Proteus) = Conversation(
        id = ID,
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

    fun channel(protocolInfo: ProtocolInfo = ProtocolInfo.Proteus) = group(protocolInfo).copy(type = Conversation.Type.Group.Channel)
}
