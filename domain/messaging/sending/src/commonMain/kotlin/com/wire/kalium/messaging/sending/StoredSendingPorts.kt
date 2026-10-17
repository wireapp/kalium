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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ProtocolInfoMapper
import com.wire.kalium.logic.data.conversation.ProtocolInfoMapperImpl
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.RegularMessageMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.client.Client
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.persistence.dao.client.InsertClientParam
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.RecipientFailureTypeEntity
import kotlin.time.Instant

/**
 * [OutgoingMessages] over the account's message table, for runtimes without `:logic`. Edits are not
 * supported: an extension sends new messages only.
 */
class StoredOutgoingMessages(
    private val messageDAO: MessageDAO,
    selfUserId: UserId,
    private val mapper: RegularMessageMapper = RegularMessageMapper(selfUserId),
) : OutgoingMessages {

    override suspend fun getMessageById(conversationId: ConversationId, messageUuid: String): Either<StorageFailure, Message> =
        wrapStorageRequest { messageDAO.getMessageById(messageUuid, conversationId.toDao()) as? MessageEntity.Regular }
            .map(mapper::fromEntity)

    override suspend fun deleteMessage(messageUuid: String, conversationId: ConversationId): Either<CoreFailure, Unit> =
        wrapStorageRequest { messageDAO.deleteMessage(messageUuid, conversationId.toDao()) }

    override suspend fun updateTextMessage(
        conversationId: ConversationId,
        messageContent: MessageContent.TextEdited,
        newMessageId: String,
        editInstant: Instant
    ): Either<CoreFailure, Unit> = Either.Left(EDITS_UNSUPPORTED)

    override suspend fun updateMultipartMessage(
        conversationId: ConversationId,
        messageContent: MessageContent.MultipartEdited,
        newMessageId: String,
        editInstant: Instant
    ): Either<CoreFailure, Unit> = Either.Left(EDITS_UNSUPPORTED)

    override suspend fun promoteMessageToSentUpdatingServerTime(
        conversationId: ConversationId,
        messageUuid: String,
        serverDate: Instant?,
        millis: Long
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        messageDAO.promoteMessageToSentUpdatingServerTime(conversationId.toDao(), messageUuid, serverDate, millis)
    }

    override suspend fun persistRecipientsDeliveryFailure(
        conversationId: ConversationId,
        messageUuid: String,
        usersWithFailedDeliveryList: List<UserId>
    ): Either<CoreFailure, Unit> =
        persistFailedRecipients(
            conversationId,
            messageUuid,
            usersWithFailedDeliveryList,
            RecipientFailureTypeEntity.MESSAGE_DELIVERY_FAILED
        )

    override suspend fun persistNoClientsToDeliverFailure(
        conversationId: ConversationId,
        messageUuid: String,
        usersWithFailedDeliveryList: List<UserId>
    ): Either<CoreFailure, Unit> =
        persistFailedRecipients(
            conversationId,
            messageUuid,
            usersWithFailedDeliveryList,
            RecipientFailureTypeEntity.NO_CLIENTS_TO_DELIVER
        )

    private suspend fun persistFailedRecipients(
        conversationId: ConversationId,
        messageUuid: String,
        users: List<UserId>,
        type: RecipientFailureTypeEntity,
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        messageDAO.insertFailedRecipientDelivery(messageUuid, conversationId.toDao(), users.map { it.toDao() }, type)
    }

    private companion object {
        val EDITS_UNSUPPORTED = StorageFailure.Generic(UnsupportedOperationException("Extensions do not send edits"))
    }
}

/** [SendingConversations] over the account's conversation and client tables. */
class StoredSendingConversations(
    private val conversationDAO: ConversationDAO,
    private val clientDAO: ClientDAO,
    private val protocolInfoMapper: ProtocolInfoMapper = ProtocolInfoMapperImpl(),
) : SendingConversations {

    override suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> =
        wrapStorageRequest { clientDAO.conversationRecipient(conversationId.toDao()) }.map { it.toRecipients() }

    override suspend fun getRecipientById(
        conversationId: ConversationId,
        userIDList: List<UserId>
    ): Either<StorageFailure, List<Recipient>> = wrapStorageRequest {
        clientDAO.recipientsIfTheyArePartOfConversation(conversationId.toDao(), userIDList.map { it.toDao() }.toSet())
    }.map { it.toRecipients() }

    override suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<StorageFailure, Conversation.ProtocolInfo> =
        wrapStorageRequest {
            conversationDAO.getConversationProtocolInfo(conversationId.toDao())
        }.map(protocolInfoMapper::fromEntity)

    private fun Map<QualifiedIDEntity, List<Client>>.toRecipients() = map { (userId, clients) ->
        Recipient(userId.toModel(), clients.map { ClientId(it.id) })
    }
}

/**
 * [ProteusRecipients] for runtimes without `:logic`: it repairs clients from the backend but refreshes
 * neither users nor the conversation, which is app work. Membership changes reach the account as events.
 */
class RemoteProteusRecipients(
    private val clientDAO: ClientDAO,
    private val clientApi: ClientApi,
) : ProteusRecipients {

    override suspend fun removeClients(clients: Map<UserId, List<ClientId>>): Either<StorageFailure, List<UserId>> =
        wrapStorageRequest {
            clientDAO.removeClientsAndReturnUsersWithNoClients(
                clients.entries.associate { (userId, clientIds) -> userId.toDao() to clientIds.map { it.value } }
            )
        }.map { users -> users.map { it.toModel() } }

    override suspend fun fetchUsers(userIds: Set<UserId>): Either<CoreFailure, Unit> = Either.Right(Unit)

    override suspend fun fetchConversation(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> = Either.Right(Unit)

    override suspend fun fetchClients(userIds: List<UserId>): Either<CoreFailure, Unit> =
        wrapApiRequest { clientApi.listClientsOfUsers(userIds.map { it.toApi() }) }.flatMap { fetched ->
            val clients = fetched.flatMap { (userId, clientList) ->
                clientList.map { client ->
                    InsertClientParam(
                        userId = QualifiedIDEntity(userId.value, userId.domain),
                        id = client.id,
                        deviceType = client.deviceClass.toEntity(),
                        clientType = null,
                        label = null,
                        model = null,
                        registrationDate = null,
                        lastActive = null,
                        mlsPublicKeys = null,
                        isMLSCapable = false,
                        isAsyncNotificationsCapable = false
                    )
                }
            }
            if (clients.isEmpty()) Either.Right(Unit)
            else wrapStorageRequest { clientDAO.insertClientsAndRemoveRedundant(clients) }
        }

    override suspend fun markClientsAsValid(clients: Map<UserId, List<ClientId>>): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            clientDAO.tryMarkValid(clients.map { (userId, clientIds) -> userId.toDao() to clientIds.map { it.value } })
        }

    private fun DeviceTypeDTO.toEntity() = when (this) {
        DeviceTypeDTO.Phone -> DeviceTypeEntity.Phone
        DeviceTypeDTO.Tablet -> DeviceTypeEntity.Tablet
        DeviceTypeDTO.Desktop -> DeviceTypeEntity.Desktop
        DeviceTypeDTO.LegalHold -> DeviceTypeEntity.LegalHold
        DeviceTypeDTO.Unknown -> DeviceTypeEntity.Unknown
    }
}
