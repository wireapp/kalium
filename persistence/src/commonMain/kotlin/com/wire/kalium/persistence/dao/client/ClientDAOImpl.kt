package com.wire.kalium.persistence.dao.client

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.wire.kalium.persistence.ClientsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.Client as SQLDelightClient

internal class ClientMapper {
    fun toModel(dbEntry: SQLDelightClient) = Client(dbEntry.user_id, dbEntry.id, dbEntry.device_type)
}

internal class ClientDAOImpl(private val clientsQueries: ClientsQueries) : ClientDAO {
    val mapper = ClientMapper()

    override suspend fun insertClient(client: Client): Unit =
        clientsQueries.insertClient(client.userId, client.id, client.deviceType)

    override suspend fun insertClients(clients: List<Client>) = clientsQueries.transaction {
        clients.forEach { client ->
            clientsQueries.insertClient(client.userId, client.id, client.deviceType)
        }
    }

    override suspend fun insertClientsAndRemoveOther(qualifiedID: QualifiedIDEntity, clients: List<Client>) = clientsQueries.transaction {
        clients.forEach { client ->
            clientsQueries.insertClient(client.userId, client.id, client.deviceType)
        }
        clientsQueries.deleteRedundantClientsForUser(qualifiedID, clients.map { it.id })
    }

    override suspend fun getClientsOfUserByQualifiedIDFlow(qualifiedID: QualifiedIDEntity): Flow<List<Client>> =
        clientsQueries.selectAllClientsByUserId(qualifiedID)
            .asFlow()
            .mapToList()
            .map { listOfEntries ->
                listOfEntries.map(mapper::toModel)
            }

    override suspend fun getClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity): List<Client> =
        clientsQueries.selectAllClientsByUserId(qualifiedID)
            .executeAsList()
            .map(mapper::toModel)

    override suspend fun getClientsOfUsersByQualifiedIDs(ids: List<QualifiedIDEntity>): Map<QualifiedIDEntity, List<Client>> =
        clientsQueries.transactionWithResult {
            ids.associateWith { qualifiedID ->
                clientsQueries.selectAllClientsByUserId(qualifiedID)
                    .executeAsList()
                    .map(mapper::toModel)
            }
        }

    override suspend fun deleteClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity): Unit =
        clientsQueries.deleteClientsOfUser(qualifiedID)

    override suspend fun deleteClient(userId: QualifiedIDEntity, clientId: String) = clientsQueries.deleteClient(userId, clientId)

    override suspend fun getClientsOfConversation(id: QualifiedIDEntity): Map<QualifiedIDEntity, List<Client>> =
        clientsQueries.selectAllClientsByConversation(id.value)
            .executeAsList()
            .map(mapper::toModel)
            .groupBy { it.userId }
}
