package com.wire.kalium.persistence.dao.client

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.wire.kalium.persistence.dao.QualifiedID
import com.wire.kalium.persistence.db.ClientsQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.db.Client as SQLDelightClient

internal class ClientMapper {
    fun toModel(dbEntry: SQLDelightClient) = Client(dbEntry.user_id, dbEntry.id)
}

internal class ClientDAOImpl(private val clientsQueries: ClientsQueries) : ClientDAO {
    val mapper = ClientMapper()

    override suspend fun insertClient(client: Client): Unit =
        clientsQueries.insertClient(client.userId, client.id)

    override suspend fun insertClients(clients: List<Client>) = clientsQueries.transaction {
        clients.forEach { client ->
            clientsQueries.insertClient(client.userId, client.id)
        }
    }

    override suspend fun getClientsOfUserByQualifiedID(qualifiedID: QualifiedID): Flow<List<Client>> =
        clientsQueries.selectAllClientsByUserId(qualifiedID)
            .asFlow()
            .mapToList()
            .map { listOfEntries ->
                listOfEntries.map(mapper::toModel)
            }

    override suspend fun deleteClientsOfUserByQualifiedID(qualifiedID: QualifiedID): Unit = clientsQueries.deleteClientsOfUser(qualifiedID)

    override suspend fun deleteClient(userId: QualifiedID, clientId: String) = clientsQueries.deleteClient(userId, clientId)
    
}
