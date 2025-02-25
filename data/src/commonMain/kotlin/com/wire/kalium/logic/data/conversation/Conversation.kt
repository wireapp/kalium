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

import com.wire.kalium.logic.data.conversation.Conversation.Access
import com.wire.kalium.logic.data.conversation.Conversation.AccessRole
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.Conversation.ReceiptMode
import com.wire.kalium.logic.data.conversation.Conversation.Type
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.message.MessagePreview
import com.wire.kalium.logic.data.message.UnreadEventType
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.util.serialization.toJsonElement
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * A conversation is a group of users that can exchange messages.
 * @param id the conversation id
 * @param name the conversation name
 * @param type the conversation [Type]
 * @param teamId the team id if the conversation was created on a team, null if there is no team
 * @param protocol the [ProtocolInfo] for the communication between users
 * @param mutedStatus the [MutedConversationStatus] informing about the conversation muting settings
 * @param removedBy the user id of the user that removed the conversation if self user was removed, null otherwise
 * @param lastNotificationDate the last notification date for the conversation, null if there are no notifications yet
 * @param lastModifiedDate the last modified date for the conversation, null if there are no modifications yet
 * @param lastReadDate the self user's last read date for the conversation
 * @param access the list of available [Access] types that can be used to invite users to the conversation
 * @param accessRole the list of available [AccessRole] user types that can be members of the conversation
 * @param creatorId the user id of the user that created the conversation
 * @param receiptMode the [ReceiptMode] flag indicating whether the conversation has read receipts enabled or not
 * @param messageTimer the enforced timer by group conversation for self deleting messages, always NULL for 1on1 conversation
 * @param userMessageTimer the timer duration picked by the user in absence of team or conversation enforced timers
 */
data class Conversation(
    val id: ConversationId,
    val name: String?,
    val type: Type,
    val teamId: TeamId?,
    val protocol: ProtocolInfo,
    val mutedStatus: MutedConversationStatus,
    val removedBy: UserId?,
    val lastNotificationDate: Instant?,
    val lastModifiedDate: Instant?,
    val lastReadDate: Instant,
    val access: List<Access>,
    val accessRole: List<AccessRole>,
    val creatorId: String?,
    val receiptMode: ReceiptMode,
    val messageTimer: Duration?,
    val userMessageTimer: Duration?,
    val archived: Boolean,
    val archivedDateTime: Instant?,
    val mlsVerificationStatus: VerificationStatus,
    val proteusVerificationStatus: VerificationStatus,
    val legalHoldStatus: LegalHoldStatus,
    val mlsPublicKeys: MLSPublicKeys? = null
) {

    companion object {
        /**
         * A set of default [AccessRole] valid for Group Conversations
         * for both personal and team users.
         *
         * @see [AccessRole]
         */
        val defaultGroupAccessRoles = setOf(AccessRole.TEAM_MEMBER, AccessRole.NON_TEAM_MEMBER)

        /**
         * A set of default [Access] modes valid for Group Conversations
         * for both personal and team users.
         *
         * @see [Access]
         */
        val defaultGroupAccess = setOf(Access.INVITE)

        /**
         * Returns a sensible set of [AccessRole] given a combination of
         * flags
         *
         * @see [AccessRole]
         */
        fun accessRolesFor(
            guestAllowed: Boolean,
            servicesAllowed: Boolean,
            nonTeamMembersAllowed: Boolean
        ): Set<AccessRole> =
            defaultGroupAccessRoles.toMutableSet().apply {
                if (servicesAllowed) {
                    add(AccessRole.SERVICE)
                } else {
                    remove(AccessRole.SERVICE)
                }

                if (guestAllowed) {
                    add(AccessRole.GUEST)
                } else {
                    remove(AccessRole.GUEST)
                }

                if (nonTeamMembersAllowed) {
                    add(AccessRole.NON_TEAM_MEMBER)
                } else {
                    remove(AccessRole.NON_TEAM_MEMBER)
                }
            }.toSet()

        /**
         * Returns a sensible set of [Access] given a combination of
         * flags
         *
         * @see [Access]
         */
        fun accessFor(
            guestsAllowed: Boolean,
        ): Set<Access> =
            defaultGroupAccess.toMutableSet().apply {
                if (guestsAllowed) {
                    add(Access.CODE)
                }
            }.toSet()

    }

    fun isTeamGroup(): Boolean = (teamId != null)

    fun isGuestAllowed(): Boolean = accessRole.let {
        (it.contains(AccessRole.GUEST))
    }

    fun isNonTeamMemberAllowed(): Boolean = accessRole.let {
        (it.contains(AccessRole.NON_TEAM_MEMBER))
    }

    fun isServicesAllowed(): Boolean = accessRole.let {
        (it.contains(AccessRole.SERVICE))
    }

    enum class Type {
        SELF,
        ONE_ON_ONE,
        GROUP,
        CONNECTION_PENDING
    }

    enum class AccessRole {
        TEAM_MEMBER,
        NON_TEAM_MEMBER,
        GUEST,
        SERVICE,
        EXTERNAL;
    }

    enum class Access {
        PRIVATE,
        INVITE,
        SELF_INVITE,
        LINK,
        CODE;
    }

    enum class Protocol {
        PROTEUS,
        MIXED,
        MLS
    }

    enum class ReceiptMode { DISABLED, ENABLED }
    enum class TypingIndicatorMode { STARTED, STOPPED }

    enum class VerificationStatus { VERIFIED, NOT_VERIFIED, DEGRADED }
    enum class LegalHoldStatus { ENABLED, DISABLED, DEGRADED, UNKNOWN }

    val supportsUnreadMessageCount
        get() = type in setOf(Type.ONE_ON_ONE, Type.GROUP)

    sealed interface ProtocolInfo {
        data object Proteus : ProtocolInfo {
            override fun name() = "Proteus"
            override fun toLogMap() = mapOf("name" to name())
        }

        data class MLS(
            override val groupId: GroupID,
            override val groupState: MLSCapable.GroupState,
            override val epoch: ULong,
            override val keyingMaterialLastUpdate: Instant,
            override val cipherSuite: CipherSuite
        ) : MLSCapable {
            override fun name() = "MLS"

            override fun toLogMap() = mapOf(
                "name" to name(),
                "groupId" to groupId.toLogString(),
                "groupState" to groupState.name,
                "epoch" to "$epoch",
                "keyingMaterialLastUpdate" to keyingMaterialLastUpdate.toString(),
                "cipherSuite" to cipherSuite.toString()
            )
        }

        data class Mixed(
            override val groupId: GroupID,
            override val groupState: MLSCapable.GroupState,
            override val epoch: ULong,
            override val keyingMaterialLastUpdate: Instant,
            override val cipherSuite: CipherSuite
        ) : MLSCapable {
            override fun name() = "Mixed"
            override fun toLogMap() = mapOf(
                "name" to name(),
                "groupId" to groupId.toLogString(),
                "groupState" to groupState.name,
                "epoch" to "$epoch",
                "keyingMaterialLastUpdate" to keyingMaterialLastUpdate.toString(),
                "cipherSuite" to cipherSuite.toString()
            )
        }

        sealed interface MLSCapable : ProtocolInfo {
            val groupId: GroupID
            val groupState: GroupState
            val epoch: ULong
            val keyingMaterialLastUpdate: Instant
            val cipherSuite: CipherSuite

            enum class GroupState { PENDING_CREATION, PENDING_JOIN, PENDING_WELCOME_MESSAGE, ESTABLISHED }
        }

        fun name(): String
        fun toLogMap(): Map<String, Any?>
    }

    @Serializable
    data class Member(
        @SerialName("id") val id: UserId,
        @SerialName("role") val role: Role
    ) {

        @Serializable
        sealed class Role {

            @Serializable
            data object Member : Role()

            @Serializable
            data object Admin : Role()

            @Serializable
            data class Unknown(@SerialName("name") val name: String) : Role()

            override fun toString(): String =
                when (this) {
                    is Member -> "member"
                    is Admin -> "admin"
                    is Unknown -> this.name
                }
        }

        override fun toString(): String {
            return "${this.toMap().toJsonElement()}"
        }

        fun toMap(): Map<String, String> = mapOf(
            "id" to id.toLogString(),
            "role" to "$role"
        )
    }
}

sealed class ConversationDetails(open val conversation: Conversation) {

    data class Team(override val conversation: Conversation) : ConversationDetails(conversation)

    data class Self(override val conversation: Conversation) : ConversationDetails(conversation)

    data class OneOne(
        override val conversation: Conversation,
        val otherUser: OtherUser,
        val userType: UserType,
        val isFavorite: Boolean = false,
        val folder: ConversationFolder? = null
    ) : ConversationDetails(conversation)

    data class Group(
        override val conversation: Conversation,
        val hasOngoingCall: Boolean = false,
        val isSelfUserMember: Boolean,
        val selfRole: Conversation.Member.Role?,
        val isFavorite: Boolean = false,
        val folder: ConversationFolder? = null,
        val wireCell: String?,
//         val isTeamAdmin: Boolean, TODO kubaz
    ) : ConversationDetails(conversation)

    data class Connection(
        val conversationId: ConversationId,
        val otherUser: OtherUser?,
        val userType: UserType,
        val lastModifiedDate: Instant,
        val connection: com.wire.kalium.logic.data.user.Connection,
        val protocolInfo: ProtocolInfo,
        val access: List<Access>,
        val accessRole: List<AccessRole>
    ) : ConversationDetails(
        Conversation(
            id = conversationId,
            name = otherUser?.name,
            type = Type.CONNECTION_PENDING,
            teamId = otherUser?.teamId,
            protocol = protocolInfo,
            mutedStatus = MutedConversationStatus.AllAllowed,
            removedBy = null,
            lastNotificationDate = null,
            lastModifiedDate = lastModifiedDate,
            lastReadDate = Instant.DISTANT_PAST,
            access = access,
            accessRole = accessRole,
            creatorId = null,
            receiptMode = ReceiptMode.DISABLED,
            messageTimer = null,
            userMessageTimer = null,
            archived = false,
            archivedDateTime = null,
            mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
        )
    )
}

data class ConversationDetailsWithEvents(
    val conversationDetails: ConversationDetails,
    val unreadEventCount: UnreadEventCount = emptyMap(),
    val lastMessage: MessagePreview? = null,
    val hasNewActivitiesToShow: Boolean = false,
)

fun ConversationDetails.interactionAvailability(): InteractionAvailability {
    val availability = when (this) {
        is ConversationDetails.Connection -> InteractionAvailability.DISABLED
        is ConversationDetails.Group -> {
            if (isSelfUserMember) InteractionAvailability.ENABLED
            else InteractionAvailability.NOT_MEMBER
        }

        is ConversationDetails.OneOne -> when {
            otherUser.defederated -> InteractionAvailability.DISABLED
            otherUser.deleted -> InteractionAvailability.DELETED_USER
            otherUser.connectionStatus == ConnectionState.BLOCKED -> InteractionAvailability.BLOCKED_USER
            conversation.legalHoldStatus == Conversation.LegalHoldStatus.DEGRADED ->
                InteractionAvailability.LEGAL_HOLD

            else -> InteractionAvailability.ENABLED
        }

        is ConversationDetails.Self, is ConversationDetails.Team -> InteractionAvailability.DISABLED
    }
    return availability
}

enum class InteractionAvailability {
    /**User is able to send messages and make calls */
    ENABLED,

    /**Self user is no longer conversation member */
    NOT_MEMBER,

    /**Other user is blocked by self user */
    BLOCKED_USER,

    /**Other team member or public user has been removed */
    DELETED_USER,

    /**
     * This indicates that the conversation is using a protocol that self user does not support.
     */
    UNSUPPORTED_PROTOCOL,

    /**Conversation type doesn't support messaging */
    DISABLED,

    /**
     * One of conversation members is under legal hold and self user is not able to interact with it.
     * This applies to 1:1 conversations only when other member is a guest.
     */
    LEGAL_HOLD
}

data class MembersInfo(val self: Conversation.Member, val otherMembers: List<Conversation.Member>)

data class MemberDetails(val user: User, val role: Conversation.Member.Role)

typealias ClientId = PlainId

data class Recipient(val id: UserId, val clients: List<ClientId>)

typealias UnreadEventCount = Map<UnreadEventType, Int>
typealias OneOnOneMembers = Map<ConversationId, UserId>
typealias GroupConversationMembers = Map<ConversationId, List<UserId>>
