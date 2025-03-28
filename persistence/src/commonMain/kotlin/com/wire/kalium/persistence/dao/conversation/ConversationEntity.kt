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
package com.wire.kalium.persistence.dao.conversation

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.datetime.Instant

data class ConversationEntity(
    val id: QualifiedIDEntity,
    val name: String?,
    val type: Type,
    val teamId: String?,
    val protocolInfo: ProtocolInfo,
    val mutedStatus: MutedStatus = MutedStatus.ALL_ALLOWED,
    val mutedTime: Long = 0,
    val removedBy: UserIDEntity? = null,
    val creatorId: String,
    val lastNotificationDate: Instant?,
    val lastModifiedDate: Instant,
    // Date that indicates when the user has seen the conversation,
    val lastReadDate: Instant,
    val access: List<Access>,
    val accessRole: List<AccessRole>,
    val receiptMode: ReceiptMode,
    val guestRoomLink: String? = null,
    val messageTimer: Long?,
    val userMessageTimer: Long?,
    val hasIncompleteMetadata: Boolean = false,
    val archived: Boolean = false,
    val archivedInstant: Instant?,
    val mlsVerificationStatus: VerificationStatus,
    val proteusVerificationStatus: VerificationStatus,
    val legalHoldStatus: LegalHoldStatus,
    val isChannel: Boolean,
    val channelAccess: ChannelAccess?,
    val channelAddPermission: ChannelAddPermission?,
) {
    enum class AccessRole { TEAM_MEMBER, NON_TEAM_MEMBER, GUEST, SERVICE, EXTERNAL; }

    enum class Access { PRIVATE, INVITE, SELF_INVITE, LINK, CODE; }

    enum class Type { SELF, ONE_ON_ONE, GROUP, CONNECTION_PENDING }

    enum class GroupState { PENDING_CREATION, PENDING_JOIN, PENDING_WELCOME_MESSAGE, ESTABLISHED }

    enum class Protocol { PROTEUS, MLS, MIXED }
    enum class ReceiptMode { DISABLED, ENABLED }
    enum class VerificationStatus { VERIFIED, NOT_VERIFIED, DEGRADED }
    enum class LegalHoldStatus { ENABLED, DISABLED, DEGRADED }
    enum class ChannelAccess { PUBLIC, PRIVATE }
    enum class ChannelAddPermission { ADMINS, EVERYONE }

    @Suppress("MagicNumber")
    enum class CipherSuite(val cipherSuiteTag: Int) {
        UNKNOWN(0),
        MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519(1),
        MLS_128_DHKEMP256_AES128GCM_SHA256_P256(2),
        MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519(3),
        MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448(4),
        MLS_256_DHKEMP521_AES256GCM_SHA512_P521(5),
        MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448(6),
        MLS_256_DHKEMP384_AES256GCM_SHA384_P384(7),
        MLS_128_X25519KYBER768DRAFT00_AES128GCM_SHA256_ED25519(61489);

        companion object {
            fun fromTag(tag: Int?): CipherSuite =
                if (tag != null) entries.first { type -> type.cipherSuiteTag == tag } else UNKNOWN
        }
    }

    enum class MutedStatus { ALL_ALLOWED, ONLY_MENTIONS_AND_REPLIES_ALLOWED, MENTIONS_MUTED, ALL_MUTED }

    sealed interface ProtocolInfo {
        data object Proteus : ProtocolInfo
        data class MLS(
            override val groupId: String,
            override val groupState: ConversationEntity.GroupState,
            override val epoch: ULong,
            override val keyingMaterialLastUpdate: Instant,
            override val cipherSuite: ConversationEntity.CipherSuite
        ) : MLSCapable

        data class Mixed(
            override val groupId: String,
            override val groupState: ConversationEntity.GroupState,
            override val epoch: ULong,
            override val keyingMaterialLastUpdate: Instant,
            override val cipherSuite: ConversationEntity.CipherSuite
        ) : MLSCapable

        sealed interface MLSCapable : ProtocolInfo {
            val groupId: String
            val groupState: ConversationEntity.GroupState
            val epoch: ULong
            val keyingMaterialLastUpdate: Instant
            val cipherSuite: ConversationEntity.CipherSuite
        }
    }
}

data class E2EIConversationClientInfoEntity(
    val userId: QualifiedIDEntity,
    val mlsGroupId: String,
    val clientId: String
)
