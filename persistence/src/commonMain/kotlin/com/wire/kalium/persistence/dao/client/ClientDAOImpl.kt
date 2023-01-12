package com.wire.kalium.persistence.dao.client

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ClientsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

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
    private val queriesContext: CoroutineContext
    private val mapper: ClientMapper = ClientMapper
) : ClientDAO {

    override suspend fun insertClient(client: InsertClientParam): Unit = withContext(queriesContext) {
        clientsQueries.insertClient(client.userId, client.id, client.deviceType, true)

    }

    override suspend fun insertClients(clients: List<InsertClientParam>) = withContext(queriesContext) {
        clientsQueries.transaction {
            clients.forEach { client ->
                clientsQueries.insertClient(client.userId, client.id, client.deviceType, true)
            }
        }
    }

    override suspend fun insertClientsAndRemoveRedundant(clients: List<InsertClientParam>) = withContext(queriesContext) {
        clientsQueries.transaction {
            clients.forEach { client ->
                clientsQueries.insertClient(client.userId, client.id, client.deviceType, true)
            }
            clients.groupBy { it.userId }.forEach { (userId, clientsList) ->
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

    override suspend fun getClientsOfUserByQualifiedIDFlow(qualifiedID: QualifiedIDEntity): Flow<List<Client>> =
        clientsQueries.selectAllClientsByUserId(qualifiedID, mapper::fromClient)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()

    override suspend fun getClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity): List<Client> = withContext(queriesContext) {
        clientsQueries.selectAllClientsByUserId(qualifiedID, mapper = mapper::fromClient)
            .executeAsList()
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
