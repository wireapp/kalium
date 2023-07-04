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

package com.wire.kalium.persistence.dao.client

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ClientsQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
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
    ): Client = Client(
        userId = user_id,
        id = id,
        deviceType = device_type,
        clientType = client_type,
        isValid = is_valid,
        isVerified = is_verified,
        registrationDate = registration_date,
        label = label,
        model = model
    )
}

@Suppress("TooManyFunctions")
internal class ClientDAOImpl internal constructor(
    private val clientsQueries: ClientsQueries,
    private val queriesContext: CoroutineContext,
    private val mapper: ClientMapper = ClientMapper
) : ClientDAO {

    /**
     * Inserts a client into the database.
     * the isValid status is always true when inserting a client but if the client already exists
     * then any new value will be ignored.
     */
    override suspend fun insertClient(client: InsertClientParam): Unit = withContext(queriesContext) {
        insert(client)
    }

    private fun insert(client: InsertClientParam) = with(client) {
        clientsQueries.insertClient(
            user_id = userId,
            id = id,
            device_type = deviceType,
            client_type = clientType,
            is_valid = true,
            registration_date = registrationDate,
            model = model,
            label = label
        )
    }

    override suspend fun insertClients(clients: List<InsertClientParam>) = withContext(queriesContext) {
        clientsQueries.transaction {
            clients.forEach { client -> insert(client) }
        }
    }

    override suspend fun insertClientsAndRemoveRedundant(clients: List<InsertClientParam>) = withContext(queriesContext) {
        clientsQueries.transaction {
            clients.groupBy { it.userId }.forEach { (userId, clientsList) ->
                clientsList.forEach { client -> insert(client) }
                clientsQueries.deleteClientsOfUserExcept(userId, clientsList.map { it.id })
            }
        }
    }

    override suspend fun tryMarkInvalid(invalidClientsList: List<Pair<QualifiedIDEntity, List<String>>>) = withContext(queriesContext) {
        clientsQueries.transaction {
            invalidClientsList.forEach { (userId, clientIdList) ->
                clientsQueries.tryMarkAsInvalid(userId, clientIdList)
            }
        }
    }

    override suspend fun updateClientVerificationStatus(userId: QualifiedIDEntity, clientId: String, verified: Boolean) =
        withContext(queriesContext) {
            clientsQueries.updateClientVerificatioStatus(verified, userId, clientId)
        }

    override suspend fun observeClient(userId: QualifiedIDEntity, clientId: String): Flow<Client?> =
        clientsQueries.selectByUserAndClientId(userId, clientId, mapper::fromClient)
            .asFlow()
            .mapToOneNotNull()
            .flowOn(queriesContext)

    override suspend fun recipientsIfTheyArePartOfConversation(
        conversationId: ConversationIDEntity,
        userIds: Set<QualifiedIDEntity>
    ): Map<QualifiedIDEntity, List<Client>> = withContext(queriesContext) {
        clientsQueries.selectRecipientsByConversationAndUserId(conversationId, userIds, mapper::fromClient)
            .executeAsList()
            .groupBy { it.userId }
    }

    override suspend fun selectAllClients(): Map<QualifiedIDEntity, List<Client>> =
        clientsQueries.selectAllClients(mapper::fromClient)
            .executeAsList()
            .groupBy { it.userId }

    override suspend fun getClientsOfUserByQualifiedIDFlow(qualifiedID: QualifiedIDEntity): Flow<List<Client>> =
        clientsQueries.selectAllClientsByUserId(qualifiedID, mapper::fromClient)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()

    override suspend fun getClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity): List<Client> = withContext(queriesContext) {
        clientsQueries.selectAllClientsByUserId(qualifiedID, mapper = mapper::fromClient)
            .executeAsList()
    }

    override suspend fun observeClientsByUserId(qualifiedID: QualifiedIDEntity): Flow<List<Client>> = withContext(queriesContext) {
        clientsQueries.selectAllClientsByUserId(qualifiedID, mapper = mapper::fromClient)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()
    }

    override suspend fun getClientsOfUsersByQualifiedIDs(
        ids: List<QualifiedIDEntity>
    ): Map<QualifiedIDEntity, List<Client>> = withContext(queriesContext) {
        clientsQueries.selectAllClientsByUserIdList(ids, mapper = mapper::fromClient)
            .executeAsList()
            .groupBy { it.userId }
    }

    override suspend fun deleteClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity): Unit = withContext(queriesContext) {
        clientsQueries.deleteClientsOfUser(qualifiedID)
    }

    override suspend fun deleteClient(
        userId: QualifiedIDEntity,
        clientId: String
    ) = withContext(queriesContext) {
        clientsQueries.deleteClient(userId, clientId)
    }

    override suspend fun getClientsOfConversation(id: QualifiedIDEntity): Map<QualifiedIDEntity, List<Client>> =
        withContext(queriesContext) {
            clientsQueries.selectAllClientsByConversation(id, mapper = mapper::fromClient)
                .executeAsList()
                .groupBy { it.userId }
        }

    override suspend fun conversationRecipient(ids: QualifiedIDEntity): Map<QualifiedIDEntity, List<Client>> = withContext(queriesContext) {
        clientsQueries.conversationRecipets(ids, mapper = mapper::fromClient)
            .executeAsList()
            .groupBy { it.userId }
    }
}
