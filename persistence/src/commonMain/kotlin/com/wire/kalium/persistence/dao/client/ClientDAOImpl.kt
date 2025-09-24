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

package com.wire.kalium.persistence.dao.client

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ClientsQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.mapToOneNotNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext

internal object ClientMapper {
    @Suppress("FunctionParameterNaming", "LongParameterList")
    fun fromClient(
        user_id: QualifiedIDEntity,
        id: String,
        device_type: DeviceTypeEntity?,
        is_valid: Boolean,
        is_verified: Boolean,
        client_type: ClientTypeEntity?,
        registration_date: Instant?,
        label: String?,
        model: String?,
        lastActive: Instant?,
        mls_public_keys: Map<String, String>?,
        is_mls_capable: Boolean,
        is_async_notifications_capable: Boolean
    ): Client = Client(
        userId = user_id,
        id = id,
        deviceType = device_type,
        clientType = client_type,
        isValid = is_valid,
        isProteusVerified = is_verified,
        isMLSCapable = is_mls_capable,
        registrationDate = registration_date,
        label = label,
        model = model,
        lastActive = lastActive,
        mlsPublicKeys = mls_public_keys,
        isAsyncNotificationsCapable = is_async_notifications_capable
    )
}

@Suppress("TooManyFunctions")
internal class ClientDAOImpl internal constructor(
    private val clientsQueries: ClientsQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
    private val mapper: ClientMapper = ClientMapper
) : ClientDAO {

    /**
     * Inserts a client into the database.
     * the isValid status is always true when inserting a client but if the client already exists
     * then any new value will be ignored.
     */
    override suspend fun insertClient(client: InsertClientParam): Unit = withContext(writeDispatcher.value) {
        clientsQueries.transaction {
            insert(client)
            val changes = clientsQueries.selectChanges().executeAsOne()
            if (changes == 0L) {
                // rollback the transaction if no changes were made so that it doesn't notify other queries if not needed
                this.rollback()
            }
        }
    }

    // returns true if any row has been inserted or modified, false if exactly the same data already exists
    private fun insert(client: InsertClientParam): Boolean = with(client) {
        clientsQueries.insertClient(
            user_id = userId,
            id = id,
            device_type = deviceType,
            client_type = clientType,
            is_valid = true,
            is_mls_capable = isMLSCapable,
            registration_date = registrationDate,
            last_active = lastActive,
            model = model,
            label = label,
            mls_public_keys = mlsPublicKeys,
            is_async_notifications_capable = isAsyncNotificationsCapable
        )
        clientsQueries.selectChanges().executeAsOne() > 0
    }

    override suspend fun insertClients(clients: List<InsertClientParam>) = withContext(writeDispatcher.value) {
        clientsQueries.transaction {
            val anyInsertedOrModified = clients.map { client -> insert(client) }.any { it }
            if (!anyInsertedOrModified) {
                // rollback the transaction if no changes were made so that it doesn't notify other queries if not needed
                this.rollback()
            }
        }
    }

    override suspend fun removeClientsAndReturnUsersWithNoClients(
        redundantClientsOfUsers: Map<UserIDEntity, List<String>>
    ) = withContext(writeDispatcher.value) {
        clientsQueries.transactionWithResult {
            redundantClientsOfUsers.entries.forEach {
                val userId = it.key
                val clients = it.value
                clientsQueries.deeteCliuentsOfUser(userId, clients)
            }
            clientsQueries.usersWithNotClients(redundantClientsOfUsers.keys).executeAsList()
        }
    }

    override suspend fun insertClientsAndRemoveRedundant(clients: List<InsertClientParam>) = withContext(writeDispatcher.value) {
        clientsQueries.transaction {
            clients.groupBy { it.userId }.forEach { (userId, clientsList) ->
                val anyInsertedOrModified = clientsList.map { client -> insert(client) }.any { it }
                clientsQueries.deleteClientsOfUserExcept(userId, clientsList.map { it.id })
                val anyDeleted = clientsQueries.selectChanges().executeAsOne() > 0
                if (!anyInsertedOrModified && !anyDeleted) {
                    // rollback the transaction if no changes were made so that it doesn't notify other queries if not needed
                    this.rollback()
                }
            }
        }
    }

    override suspend fun tryMarkInvalid(invalidClientsList: List<Pair<QualifiedIDEntity, List<String>>>) = withContext(writeDispatcher.value) {
        clientsQueries.transaction {
            invalidClientsList.forEach { (userId, clientIdList) ->
                clientsQueries.tryMarkAsInvalid(userId, clientIdList)
            }
        }
    }

    override suspend fun updateClientProteusVerificationStatus(userId: QualifiedIDEntity, clientId: String, verified: Boolean) =
        withContext(writeDispatcher.value) {
            clientsQueries.updateClientProteusVerificationStatus(verified, userId, clientId)
        }

    override suspend fun observeClient(userId: QualifiedIDEntity, clientId: String): Flow<Client?> =
        clientsQueries.selectByUserAndClientId(userId, clientId, mapper::fromClient)
            .asFlow()
            .mapToOneNotNull()
            .flowOn(readDispatcher.value)

    override suspend fun recipientsIfTheyArePartOfConversation(
        conversationId: ConversationIDEntity,
        userIds: Set<QualifiedIDEntity>
    ): Map<QualifiedIDEntity, List<Client>> = withContext(readDispatcher.value) {
        clientsQueries.selectRecipientsByConversationAndUserId(conversationId, userIds, mapper::fromClient)
            .executeAsList()
            .groupBy { it.userId }
    }

    // TODO(MO): instead of selecting as list and then grouping, do the grouping in SQL directly
    override suspend fun selectAllClients(): Map<QualifiedIDEntity, List<Client>> = withContext(readDispatcher.value) {
        clientsQueries.selectAllClients(mapper::fromClient)
            .executeAsList()
            .groupBy { it.userId }
    }

    override suspend fun isMLSCapable(userId: QualifiedIDEntity, clientId: String): Boolean? = withContext(readDispatcher.value) {
        clientsQueries.isClientMLSCapable(userId, clientId)
            .executeAsOneOrNull()
    }

    override suspend fun getClientsOfUserByQualifiedIDFlow(qualifiedID: QualifiedIDEntity): Flow<List<Client>> =
        clientsQueries.selectAllClientsByUserId(qualifiedID, mapper::fromClient)
            .asFlow()
            .flowOn(readDispatcher.value)
            .mapToList()

    override suspend fun getClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity): List<Client> = withContext(readDispatcher.value) {
        clientsQueries.selectAllClientsByUserId(qualifiedID, mapper = mapper::fromClient)
            .executeAsList()
    }

    override suspend fun observeClientsByUserId(qualifiedID: QualifiedIDEntity): Flow<List<Client>> = withContext(readDispatcher.value) {
        clientsQueries.selectAllClientsByUserId(qualifiedID, mapper = mapper::fromClient)
            .asFlow()
            .flowOn(readDispatcher.value)
            .mapToList()
    }

    override suspend fun getClientsOfUsersByQualifiedIDs(
        ids: List<QualifiedIDEntity>
    ): Map<QualifiedIDEntity, List<Client>> = withContext(readDispatcher.value) {
        clientsQueries.selectAllClientsByUserIdList(ids, mapper = mapper::fromClient)
            .executeAsList()
            .groupBy { it.userId }
    }

    override suspend fun deleteClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity): Unit = withContext(writeDispatcher.value) {
        clientsQueries.deleteClientsOfUser(qualifiedID)
    }

    override suspend fun deleteClient(
        userId: QualifiedIDEntity,
        clientId: String
    ) = withContext(writeDispatcher.value) {
        clientsQueries.deleteClient(userId, clientId)
    }

    override suspend fun getClientsOfConversation(id: QualifiedIDEntity): Map<QualifiedIDEntity, List<Client>> =
        withContext(readDispatcher.value) {
            clientsQueries.selectAllClientsByConversation(id, mapper = mapper::fromClient)
                .executeAsList()
                .groupBy { it.userId }
        }

    override suspend fun conversationRecipient(ids: QualifiedIDEntity): Map<QualifiedIDEntity, List<Client>> = withContext(readDispatcher.value) {
        clientsQueries.conversationRecipets(ids, mapper = mapper::fromClient)
            .executeAsList()
            .groupBy { it.userId }
    }
}
