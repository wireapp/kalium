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

package com.wire.kalium.messaging.sending

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.client.ClientDTO
import com.wire.kalium.network.api.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.authenticated.client.RegisterClientRequest
import com.wire.kalium.network.api.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.authenticated.client.UpdateClientCapabilitiesRequest
import com.wire.kalium.network.api.authenticated.client.UpdateClientMlsPublicKeysRequest
import com.wire.kalium.network.api.model.PushTokenBody
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.model.UserId as NetworkUserId
import com.wire.kalium.network.utils.NetworkResponse
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
import com.wire.kalium.persistence.dao.message.DeliveryStatusEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.withTestUserDatabase
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class StoredSendingPortsTest {

    @Test
    fun storedMessageIsReadPromotedAndKeepsItsFailedRecipients() = runTest {
        withDatabase { database ->
            database.messageDAO.insertOrIgnoreMessage(message())
            val messages = StoredOutgoingMessages(database.messageDAO, SELF)

            val stored = assertIs<Either.Right<Message>>(messages.getMessageById(CONVERSATION, MESSAGE_ID)).value
            assertEquals(MessageContent.Text("Hello"), stored.content)

            messages.promoteMessageToSentUpdatingServerTime(CONVERSATION, MESSAGE_ID, SENT_AT, 0)
            messages.persistNoClientsToDeliverFailure(CONVERSATION, MESSAGE_ID, listOf(OTHER))

            val updated = database.messageDAO.getMessageById(MESSAGE_ID, CONVERSATION.toEntity())
            assertEquals(MessageEntity.Status.SENT, updated?.status)
            val delivery = assertIs<DeliveryStatusEntity.PartialDelivery>((updated as MessageEntity.Regular).deliveryStatus)
            assertEquals(listOf(OTHER.toEntity()), delivery.recipientsFailedWithNoClients)
        }
    }

    @Test
    fun editsAreNotSentFromExtensions() = runTest {
        withDatabase { database ->
            val result = StoredOutgoingMessages(database.messageDAO, SELF)
                .updateTextMessage(CONVERSATION, MessageContent.TextEdited(MESSAGE_ID, "Edited"), "new", SENT_AT)

            assertIs<Either.Left<*>>(result)
        }
    }

    @Test
    fun recipientsAndProtocolComeFromTheStoredConversation() = runTest {
        withDatabase { database ->
            database.clientDAO.insertClients(listOf(client(OTHER, "other-phone")))
            val conversations = StoredSendingConversations(database.conversationDAO, database.clientDAO)

            assertEquals(
                Either.Right(listOf(Recipient(OTHER, listOf(ClientId("other-phone"))))),
                conversations.getConversationRecipients(CONVERSATION).map { recipients -> recipients.filter { it.id == OTHER } }
            )
            assertEquals(Either.Right(Conversation.ProtocolInfo.Proteus), conversations.getConversationProtocolInfo(CONVERSATION))
        }
    }

    @Test
    fun clientRepairRemovesDeletedClientsAndStoresFetchedOnes() = runTest {
        withDatabase { database ->
            database.clientDAO.insertClients(listOf(client(OTHER, "old")))
            val api = FakeClientApi(mapOf(NetworkUserId(OTHER.value, OTHER.domain) to listOf(SimpleClientResponse("new", DeviceTypeDTO.LegalHold))))
            val recipients = RemoteProteusRecipients(database.clientDAO, api)

            assertEquals(Either.Right(listOf(OTHER)), recipients.removeClients(mapOf(OTHER to listOf(ClientId("old")))))
            assertIs<Either.Right<Unit>>(recipients.fetchClients(listOf(OTHER)))
            assertIs<Either.Right<Unit>>(recipients.markClientsAsValid(mapOf(OTHER to listOf(ClientId("new")))))

            val stored = database.clientDAO.getClientsOfUserByQualifiedID(OTHER.toEntity()).single()
            assertEquals("new" to DeviceTypeEntity.LegalHold, stored.id to stored.deviceType)
            assertEquals(listOf(OTHER), api.requested)
        }
    }

    private suspend fun TestScope.withDatabase(block: suspend (UserDatabaseBuilder) -> Unit) =
        withTestUserDatabase(SELF.toEntity()) { database ->
            with(database.builder) {
                userDAO.upsertUsers(listOf(user(SELF), user(OTHER)))
                conversationDAO.insertConversation(conversation())
                memberDAO.insertMembersWithQualifiedId(
                    listOf(MemberEntity(SELF.toEntity(), MemberEntity.Role.Admin), MemberEntity(OTHER.toEntity(), MemberEntity.Role.Member)),
                    CONVERSATION.toEntity()
                )
            }
            block(database.builder)
        }

    private class FakeClientApi(private val clients: Map<NetworkUserId, List<SimpleClientResponse>>) : ClientApi {
        val requested = mutableListOf<UserId>()

        override suspend fun listClientsOfUsers(userIds: List<NetworkUserId>): NetworkResponse<Map<NetworkUserId, List<SimpleClientResponse>>> {
            requested += userIds.map { UserId(it.value, it.domain) }
            return NetworkResponse.Success(clients, emptyMap(), 200)
        }

        override suspend fun registerClient(registerClientRequest: RegisterClientRequest): NetworkResponse<ClientDTO> = error("Unused")
        override suspend fun fetchSelfUserClient(): NetworkResponse<List<ClientDTO>> = error("Unused")
        override suspend fun deleteClient(password: String?, clientID: String): NetworkResponse<Unit> = error("Unused")
        override suspend fun fetchClientInfo(clientID: String): NetworkResponse<ClientDTO> = error("Unused")
        override suspend fun updateClientMlsPublicKeys(
            updateClientMlsPublicKeysRequest: UpdateClientMlsPublicKeysRequest,
            clientID: String
        ): NetworkResponse<Unit> = error("Unused")
        override suspend fun updateClientCapabilities(
            updateClientCapabilitiesRequest: UpdateClientCapabilitiesRequest,
            clientID: String
        ): NetworkResponse<Unit> = error("Unused")
        override suspend fun registerToken(body: PushTokenBody): NetworkResponse<Unit> = error("Unused")
        override suspend fun deregisterToken(pid: String): NetworkResponse<Unit> = error("Unused")
    }

    private companion object {
        val SELF = UserId("self", "wire.com")
        val OTHER = UserId("other", "wire.com")
        val CONVERSATION = ConversationId("conversation", "wire.com")
        const val MESSAGE_ID = "message"
        val SENT_AT = Instant.parse("2026-10-13T12:00:00Z")

        fun UserId.toEntity() = QualifiedIDEntity(value, domain)

        fun user(id: UserId) = UserEntity(
            id = id.toEntity(),
            name = id.value,
            handle = id.value,
            email = null,
            phone = null,
            accentId = 1,
            team = null,
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            previewAssetId = null,
            completeAssetId = null,
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = UserTypeEntity.STANDARD,
            botService = null,
            deleted = false,
            expiresAt = null,
            defederated = false,
            supportedProtocols = setOf(SupportedProtocolEntity.PROTEUS),
            activeOneOnOneConversationId = null,
        )

        fun conversation() = ConversationEntity(
            id = CONVERSATION.toEntity(),
            name = "Team",
            type = ConversationEntity.Type.GROUP,
            teamId = null,
            protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
            creatorId = SELF.value,
            lastNotificationDate = null,
            lastModifiedDate = SENT_AT,
            lastReadDate = SENT_AT,
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

        fun message() = MessageEntity.Regular(
            id = MESSAGE_ID,
            content = MessageEntityContent.Text("Hello"),
            conversationId = CONVERSATION.toEntity(),
            date = SENT_AT,
            senderUserId = SELF.toEntity(),
            senderClientId = "self-client",
            status = MessageEntity.Status.PENDING,
            editStatus = MessageEntity.EditStatus.NotEdited,
            visibility = MessageEntity.Visibility.VISIBLE,
            senderName = null,
            expectsReadConfirmation = false,
            readCount = 0,
        )

        fun client(userId: UserId, id: String) = InsertClientParam(
            userId = userId.toEntity(),
            id = id,
            deviceType = DeviceTypeEntity.Phone,
            clientType = null,
            label = null,
            model = null,
            registrationDate = null,
            lastActive = null,
            mlsPublicKeys = null,
            isMLSCapable = false,
            isAsyncNotificationsCapable = false,
        )
    }
}
