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

package com.wire.kalium.share

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.persistence.dao.client.InsertClientParam
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ShareBlockTest {

    @Test
    fun onlyConversationsTheAppLetsTheUserWriteToAreListedNewestFirst() = runTest {
        withDatabase { database ->
            seed(database)

            assertEquals(listOf("group", "one"), database.shareableConversations().map { it.id.value })
        }
    }

    @Test
    fun conversationsNeedingConsentOrAppWorkAreDeclined() = runTest {
        withDatabase { database ->
            seed(database)

            suspend fun block(id: String) = database.shareBlock(database.conversationDAO.getConversationDetailsById(QualifiedIDEntity(id, DOMAIN))!!)
            assertNull(block("group"))
            assertNull(block("one"))
            assertEquals(ShareBlock.UNAVAILABLE, block("left"))
            assertEquals(ShareBlock.UNAVAILABLE, block("blocked"))
            assertEquals(ShareBlock.LEGAL_HOLD, block("hold"))
            assertEquals(ShareBlock.LEGAL_HOLD, block("hold-unnotified"))
            assertEquals(ShareBlock.LEGAL_HOLD, block("hold-device"))
            assertEquals(ShareBlock.VERIFICATION, block("degraded"))
            assertEquals(ShareBlock.VERIFICATION, block("mls-degraded"))
            assertEquals(ShareBlock.MLS_GROUP, block("joining"))
        }
    }

    @Test
    fun sharedTextsGetTheTimerTheAppWouldGive() = runTest {
        withDatabase { database ->
            seed(database)
            val group = database.conversationDAO.getConversationDetailsById(QualifiedIDEntity("timed", DOMAIN))!!
            val cell = database.conversationDAO.getConversationDetailsById(QualifiedIDEntity("cell", DOMAIN))!!

            assertEquals(SelfDeletionTimer.Enforced.ByGroup(1.minutes), group.shareSelfDeletionTimer(TeamSelfDeleteTimer.Enabled))
            assertEquals(SelfDeletionTimer.Enforced.ByTeam(5.seconds), group.shareSelfDeletionTimer(TeamSelfDeleteTimer.Enforced(5.seconds)))
            assertEquals(SelfDeletionTimer.Disabled, cell.shareSelfDeletionTimer(TeamSelfDeleteTimer.Enforced(5.seconds)))
        }
    }

    @Test
    fun sharedTextIsAPendingMessageFromThisClient() {
        val message = sharedMessage(
            id = "id",
            content = MessageContent.Text("https://wire.com"),
            conversationId = ConversationId("group", DOMAIN),
            selfUserId = SELF,
            selfClientId = ClientId("client"),
            now = T1,
            timer = SelfDeletionTimer.Enforced.ByGroup(1.minutes),
            expectsReadConfirmation = true,
        )

        assertEquals(MessageContent.Text("https://wire.com"), message.content)
        assertEquals(Message.Status.Pending, message.status)
        assertEquals(ClientId("client"), message.senderClientId)
        assertEquals(1.minutes, message.expirationData?.expireAfter)
        assertEquals(true, message.expectsReadConfirmation)
        assertEquals(true, message.isSelfMessage)
    }

    private suspend fun seed(database: UserDatabaseBuilder) {
        database.userDAO.upsertUsers(
            listOf(
                user(SELF, ConnectionEntity.State.ACCEPTED),
                user(OTHER, ConnectionEntity.State.ACCEPTED, activeOneOnOne = "one"),
                user(BLOCKED, ConnectionEntity.State.BLOCKED, activeOneOnOne = "blocked"),
                user(WATCHED, ConnectionEntity.State.ACCEPTED),
            )
        )
        conversation(database, "group", ConversationEntity.Type.GROUP, T3)
        conversation(database, "one", ConversationEntity.Type.ONE_ON_ONE, T2)
        conversation(database, "left", ConversationEntity.Type.GROUP, T3, selfIsMember = false)
        conversation(database, "blocked", ConversationEntity.Type.ONE_ON_ONE, T3, other = BLOCKED)
        // Unverified Proteus conversations are stored as degraded; only an unacknowledged change needs the app.
        conversation(database, "hold", ConversationEntity.Type.GROUP, T3)
        database.conversationDAO.updateLegalHoldStatus(QualifiedIDEntity("hold", DOMAIN), ConversationEntity.LegalHoldStatus.DEGRADED)
        conversation(database, "hold-unnotified", ConversationEntity.Type.GROUP, T3)
        database.conversationDAO.updateLegalHoldStatus(QualifiedIDEntity("hold-unnotified", DOMAIN), ConversationEntity.LegalHoldStatus.ENABLED)
        database.conversationDAO.updateLegalHoldStatusChangeNotified(QualifiedIDEntity("hold-unnotified", DOMAIN), false)
        // A legal hold device the app has not applied to the conversation yet.
        conversation(database, "hold-device", ConversationEntity.Type.GROUP, T3, other = WATCHED)
        database.clientDAO.insertClients(listOf(legalHoldClient(WATCHED)))
        conversation(database, "degraded", ConversationEntity.Type.GROUP, T3)
        database.conversationDAO.updateDegradedConversationNotifiedFlag(QualifiedIDEntity("degraded", DOMAIN), false)
        conversation(database, "mls-degraded", ConversationEntity.Type.GROUP, T3)
        database.conversationDAO.updateMlsVerificationStatus(
            ConversationEntity.VerificationStatus.DEGRADED,
            QualifiedIDEntity("mls-degraded", DOMAIN)
        )
        conversation(database, "joining", ConversationEntity.Type.GROUP, T3) {
            copy(
                protocolInfo = ConversationEntity.ProtocolInfo.MLS(
                    "joining-group",
                    ConversationEntity.GroupState.PENDING_JOIN,
                    0UL,
                    T1,
                    ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            )
        }
        conversation(database, "archived", ConversationEntity.Type.GROUP, T3) { copy(archived = true, archivedInstant = T1) }
        conversation(database, "timed", ConversationEntity.Type.GROUP, T1) { copy(archived = true, archivedInstant = T1, messageTimer = 60_000) }
        conversation(database, "cell", ConversationEntity.Type.GROUP, T1) {
            copy(archived = true, archivedInstant = T1, messageTimer = 60_000, wireCell = "cell")
        }
    }

    @Suppress("LongParameterList")
    private suspend fun conversation(
        database: UserDatabaseBuilder,
        id: String,
        type: ConversationEntity.Type,
        lastModified: Instant,
        selfIsMember: Boolean = true,
        other: UserId = OTHER,
        change: ConversationEntity.() -> ConversationEntity = { this },
    ) {
        val conversationId = QualifiedIDEntity(id, DOMAIN)
        database.conversationDAO.insertConversation(conversationEntity(conversationId, type, lastModified).change())
        database.memberDAO.insertMembersWithQualifiedId(
            listOfNotNull(
                MemberEntity(SELF.toEntity(), MemberEntity.Role.Admin).takeIf { selfIsMember },
                MemberEntity(other.toEntity(), MemberEntity.Role.Member),
            ),
            conversationId
        )
    }

    private suspend fun TestScope.withDatabase(block: suspend (UserDatabaseBuilder) -> Unit) {
        val database = TestUserDatabase(SELF.toEntity(), StandardTestDispatcher(testScheduler))
        try {
            block(database.builder)
        } finally {
            database.builder.close()
            database.delete()
        }
    }

    private companion object {
        const val DOMAIN = "wire.com"
        val SELF = UserId("self", DOMAIN)
        val OTHER = UserId("other", DOMAIN)
        val BLOCKED = UserId("blocked", DOMAIN)
        val WATCHED = UserId("watched", DOMAIN)
        val T1 = Instant.parse("2026-10-13T10:00:00Z")
        val T2 = Instant.parse("2026-10-13T11:00:00Z")
        val T3 = Instant.parse("2026-10-13T12:00:00Z")

        fun UserId.toEntity() = QualifiedIDEntity(value, domain)

        fun user(id: UserId, connection: ConnectionEntity.State, activeOneOnOne: String? = null) = UserEntity(
            id = id.toEntity(),
            name = id.value,
            handle = id.value,
            email = null,
            phone = null,
            accentId = 1,
            team = null,
            connectionStatus = connection,
            previewAssetId = null,
            completeAssetId = null,
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = UserTypeEntity.STANDARD,
            botService = null,
            deleted = false,
            expiresAt = null,
            defederated = false,
            supportedProtocols = setOf(SupportedProtocolEntity.PROTEUS),
            activeOneOnOneConversationId = activeOneOnOne?.let { QualifiedIDEntity(it, DOMAIN) },
        )

        fun legalHoldClient(userId: UserId) = InsertClientParam(
            userId = userId.toEntity(),
            id = "legal-hold",
            deviceType = DeviceTypeEntity.LegalHold,
            clientType = null,
            label = null,
            model = null,
            registrationDate = null,
            lastActive = null,
            mlsPublicKeys = null,
            isMLSCapable = false,
            isAsyncNotificationsCapable = false,
        )

        fun conversationEntity(id: QualifiedIDEntity, type: ConversationEntity.Type, lastModified: Instant) = ConversationEntity(
            id = id,
            name = id.value,
            type = type,
            teamId = null,
            protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
            creatorId = SELF.value,
            lastNotificationDate = null,
            lastModifiedDate = lastModified,
            lastReadDate = T1,
            access = emptyList(),
            accessRole = emptyList(),
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
            historySharingRetentionSeconds = 0,
        )
    }
}
