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
import com.wire.kalium.persistence.dao.TeamEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ProposalTimerEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant

internal object TestStubs {
    const val teamId = "teamId"

    val user1 = newUserEntity(id = "1").copy(team = teamId)
    val user2 = newUserEntity(id = "2").copy(team = teamId)
    val user3 = newUserEntity(id = "3").copy(team = teamId)
    val userDetails1 = newUserDetailsEntity(id = "1").copy(team = teamId)
    val messageTimer = 5000L

    val team = TeamEntity(teamId, "teamName", "")

    val conversationEntity1 = ConversationEntity(
        QualifiedIDEntity("1", "wire.com"),
        "conversation1",
        ConversationEntity.Type.ONE_ON_ONE,
        teamId,
        ConversationEntity.ProtocolInfo.Proteus,
        creatorId = "someValue",
        lastNotificationDate = null,
        lastModifiedDate = "2022-03-30T15:36:00.000Z".toInstant(),
        lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
        mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
        access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
        receiptMode = ConversationEntity.ReceiptMode.DISABLED,
        messageTimer = messageTimer,
        userMessageTimer = null,
        archived = false,
        archivedInstant = null,
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
        wireCell = null,
    )
    val conversationEntity2 = ConversationEntity(
        QualifiedIDEntity("2", "wire.com"),
        "conversation2",
        ConversationEntity.Type.ONE_ON_ONE,
        null,
        ConversationEntity.ProtocolInfo.MLS(
            "group2",
            ConversationEntity.GroupState.ESTABLISHED,
            0UL,
            Instant.parse("2021-03-30T15:36:00.000Z"),
            cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        ),
        creatorId = "someValue",
        lastNotificationDate = null,
        lastModifiedDate = "2021-03-30T15:36:00.000Z".toInstant(),
        lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
        mutedStatus = ConversationEntity.MutedStatus.ALL_MUTED,
        access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
        receiptMode = ConversationEntity.ReceiptMode.DISABLED,
        messageTimer = messageTimer,
        userMessageTimer = null,
        archived = false,
        archivedInstant = null,
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
        wireCell = null,
    )

    val conversationEntity3 = ConversationEntity(
        QualifiedIDEntity("3", "wire.com"),
        "conversation3",
        ConversationEntity.Type.GROUP,
        null,
        ConversationEntity.ProtocolInfo.MLS(
            "group3",
            ConversationEntity.GroupState.PENDING_JOIN,
            0UL,
            Instant.parse("2021-03-30T15:36:00.000Z"),
            cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        ),
        creatorId = "someValue",
        // This conversation was modified after the last time the user was notified about it
        lastNotificationDate = "2021-03-30T15:30:00.000Z".toInstant(),
        lastModifiedDate = "2021-03-30T15:36:00.000Z".toInstant(),
        lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
        // and it's status is set to be only notified if there is a mention for the user
        mutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED,
        access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
        receiptMode = ConversationEntity.ReceiptMode.DISABLED,
        messageTimer = messageTimer,
        userMessageTimer = null,
        archived = false,
        archivedInstant = null,
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
        wireCell = null,
    )

    val conversationEntity4 = ConversationEntity(
        QualifiedIDEntity("4", "wire.com"),
        "conversation4",
        ConversationEntity.Type.GROUP,
        null,
        ConversationEntity.ProtocolInfo.MLS(
            "group4",
            ConversationEntity.GroupState.ESTABLISHED,
            0UL,
            Instant.parse("2021-03-30T15:36:00.000Z"),
            cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        ),
        creatorId = "someValue",
        // This conversation was modified after the last time the user was notified about it
        lastNotificationDate = "2021-03-30T15:30:00.000Z".toInstant(),
        lastModifiedDate = "2021-03-30T15:36:00.000Z".toInstant(),
        lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
        // and it's status is set to be only notified if there is a mention for the user
        mutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED,
        access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
        receiptMode = ConversationEntity.ReceiptMode.DISABLED,
        messageTimer = messageTimer,
        userMessageTimer = null,
        archived = false,
        archivedInstant = null,
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
        wireCell = null,
    )

    val conversationEntity5 = ConversationEntity(
        QualifiedIDEntity("5", "wire.com"),
        "conversation4",
        ConversationEntity.Type.GROUP,
        null,
        ConversationEntity.ProtocolInfo.Mixed(
            "group4",
            ConversationEntity.GroupState.ESTABLISHED,
            0UL,
            Instant.parse("2021-03-30T15:36:00.000Z"),
            cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        ),
        creatorId = "someValue",
        // This conversation was modified after the last time the user was notified about it
        lastNotificationDate = "2021-03-30T15:30:00.000Z".toInstant(),
        lastModifiedDate = "2021-03-30T15:36:00.000Z".toInstant(),
        lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
        // and it's status is set to be only notified if there is a mention for the user
        mutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_AND_REPLIES_ALLOWED,
        access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
        accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
        receiptMode = ConversationEntity.ReceiptMode.DISABLED,
        messageTimer = messageTimer,
        userMessageTimer = null,
        archived = false,
        archivedInstant = null,
        mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
        legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
        wireCell = null,
    )

    val member1 = MemberEntity(user1.id, MemberEntity.Role.Admin)
    val member2 = MemberEntity(user2.id, MemberEntity.Role.Member)
    val member3 = MemberEntity(user3.id, MemberEntity.Role.Admin)

    val proposalTimer2 = ProposalTimerEntity(
        (conversationEntity2.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId,
        Instant.DISTANT_FUTURE
    )

    val proposalTimer3 = ProposalTimerEntity(
        (conversationEntity3.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId,
        Instant.DISTANT_FUTURE
    )
}
