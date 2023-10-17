/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.message.MessagePreview
import com.wire.kalium.logic.data.message.UnreadEventType
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.util.serialization.toJsonElement
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.datetime.Instant
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
    val lastNotificationDate: String?,
    val lastModifiedDate: String?,
    val lastReadDate: String,
    val access: List<Access>,
    val accessRole: List<AccessRole>,
    val creatorId: String?,
    val receiptMode: ReceiptMode,
    val messageTimer: Duration?,
    val userMessageTimer: Duration?
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

    enum class ReceiptMode { DISABLED, ENABLED }

    @Suppress("MagicNumber")
    enum class CipherSuite(val tag: Int) {
        UNKNOWN(0),
        MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519(1),
        MLS_128_DHKEMP256_AES128GCM_SHA256_P256(2),
        MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519(3),
        MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448(4),
        MLS_256_DHKEMP521_AES256GCM_SHA512_P521(5),
        MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448(6),
        MLS_256_DHKEMP384_AES256GCM_SHA384_P384(7);

        companion object {
            fun fromTag(tag: Int): CipherSuite = values().first { type -> type.tag == tag }
        }
    }

    val supportsUnreadMessageCount
        get() = type in setOf(Type.ONE_ON_ONE, Type.GROUP)

    sealed class ProtocolInfo {
        data object Proteus : ProtocolInfo() {
            override fun name() = "Proteus"
        }

        data class MLS(
            val groupId: GroupID,
            val groupState: GroupState,
            val epoch: ULong,
            val keyingMaterialLastUpdate: Instant,
            val cipherSuite: CipherSuite
        ) : ProtocolInfo() {
            enum class GroupState { PENDING_CREATION, PENDING_JOIN, PENDING_WELCOME_MESSAGE, ESTABLISHED }

            override fun name() = "MLS"
        }

        abstract fun name(): String
    }

    enum class Protocol { PROTEUS, MLS }

    data class Member(val id: UserId, val role: Role) {
        sealed class Role {
            data object Member : Role()
            data object Admin : Role()
            data class Unknown(val name: String) : Role()

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
        val legalHoldStatus: LegalHoldStatus,
        val userType: UserType,
        val unreadEventCount: UnreadEventCount,
        val lastMessage: MessagePreview?
    ) : ConversationDetails(conversation)

    data class Group(
        override val conversation: Conversation,
        val legalHoldStatus: LegalHoldStatus,
        val hasOngoingCall: Boolean = false,
        val unreadEventCount: UnreadEventCount,
        val lastMessage: MessagePreview?,
        val isSelfUserMember: Boolean,
        val isSelfUserCreator: Boolean,
        val selfRole: Conversation.Member.Role?
//         val isTeamAdmin: Boolean, TODO kubaz
    ) : ConversationDetails(conversation)

    data class Connection(
        val conversationId: ConversationId,
        val otherUser: OtherUser?,
        val userType: UserType,
        val lastModifiedDate: String,
        val connection: com.wire.kalium.logic.data.user.Connection,
        val protocolInfo: Conversation.ProtocolInfo,
        val access: List<Conversation.Access>,
        val accessRole: List<Conversation.AccessRole>
    ) : ConversationDetails(
        Conversation(
            id = conversationId,
            name = otherUser?.name,
            type = Conversation.Type.CONNECTION_PENDING,
            teamId = otherUser?.teamId,
            protocol = protocolInfo,
            mutedStatus = MutedConversationStatus.AllAllowed,
            removedBy = null,
            lastNotificationDate = null,
            lastModifiedDate = lastModifiedDate,
            lastReadDate = UNIX_FIRST_DATE,
            access = access,
            accessRole = accessRole,
            creatorId = null,
            receiptMode = Conversation.ReceiptMode.DISABLED,
            messageTimer = null,
            userMessageTimer = null
        )
    )
}

data class MembersInfo(val self: Conversation.Member, val otherMembers: List<Conversation.Member>)

data class MemberDetails(val user: User, val role: Conversation.Member.Role)

typealias ClientId = PlainId

data class Recipient(val id: UserId, val clients: List<ClientId>)

typealias UnreadEventCount = Map<UnreadEventType, Int>
typealias OneOnOneMembers = Map<ConversationId, UserId>
typealias GroupConversationMembers = Map<ConversationId, List<UserId>>
