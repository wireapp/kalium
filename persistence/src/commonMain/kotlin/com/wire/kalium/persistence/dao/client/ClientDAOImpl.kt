package com.wire.kalium.persistence.dao.client

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.wire.kalium.persistence.ClientsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow

internal object ClientMapper {
    @Suppress("FunctionParameterNaming")
    fun fromClient(
        user_id: QualifiedIDEntity,
        id: String,
        device_type: DeviceTypeEntity?,
        is_valid: Boolean
    ): Client = Client(user_id, id, device_type, is_valid)
}

internal class ClientDAOImpl internal constructor(
    private val clientsQueries: ClientsQueries,
    private val mapper: ClientMapper = ClientMapper
) : ClientDAO {

    override suspend fun insertClient(client: InsertClientParam): Unit =
        clientsQueries.insertClient(client.userId, client.id, client.deviceType, true)

    override suspend fun insertClients(clients: List<InsertClientParam>) = clientsQueries.transaction {
        clients.forEach { client ->
            clientsQueries.insertClient(client.userId, client.id, client.deviceType, true)
        }
    }

    override suspend fun insertClientsAndRemoveRedundant(clients: List<InsertClientParam>) =
        clientsQueries.transaction {
            clients.forEach { client ->
                clientsQueries.insertClient(client.userId, client.id, client.deviceType, true)
            }
            clients.groupBy { it.userId }.forEach { (userId, clientsList) ->
                clientsQueries.deleteClientsOfUserExcept(userId, clientsList.map { it.id })
            }
        }

    override suspend fun tryMarkInvalid(invalidClientsList: List<Pair<QualifiedIDEntity, List<String>>>) = clientsQueries.transaction {
        invalidClientsList.forEach { (userId, clientIdList) ->
            clientsQueries.tryMarkAsInvalid(userId, clientIdList)
        }
    }

    override suspend fun getClientsOfUserByQualifiedIDFlow(qualifiedID: QualifiedIDEntity): Flow<List<Client>> =
        clientsQueries.selectAllClientsByUserId(qualifiedID, mapper::fromClient)
            .asFlow()
            .mapToList()

    override suspend fun getClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity): List<Client> =
        clientsQueries.selectAllClientsByUserId(qualifiedID, mapper = mapper::fromClient)
            .executeAsList()

    override suspend fun getClientsOfUsersByQualifiedIDs(
        ids: List<QualifiedIDEntity>
    ): Map<QualifiedIDEntity, List<Client>> =
        clientsQueries.selectAllClientsByUserIdList(ids, mapper = mapper::fromClient)
            .executeAsList()
            .groupBy { it.userId }

    override suspend fun deleteClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity): Unit =
        clientsQueries.deleteClientsOfUser(qualifiedID)

    override suspend fun deleteClient(
        userId: QualifiedIDEntity,
        clientId: String
    ) = clientsQueries.deleteClient(userId, clientId)

    override suspend fun getClientsOfConversation(id: QualifiedIDEntity): Map<QualifiedIDEntity, List<Client>> =
        clientsQueries.selectAllClientsByConversation(id, mapper = mapper::fromClient)
            .executeAsList()
            .groupBy { it.userId }

    override suspend fun conversationRepents(ids: QualifiedIDEntity): Map<QualifiedIDEntity, List<Client>> =
        clientsQueries.conversationRecipets(ids, mapper = mapper::fromClient)
            .executeAsList().groupBy { it.userId }

}
