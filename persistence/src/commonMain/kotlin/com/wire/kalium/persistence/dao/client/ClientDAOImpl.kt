package com.wire.kalium.persistence.dao.client

import com.wire.kalium.persistence.ClientsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.Client as SQLDelightClient

internal class ClientMapper {
    fun toModel(dbEntry: SQLDelightClient) = Client(dbEntry.user_id, dbEntry.id)
}

internal class ClientDAOImpl(private val clientsQueries: ClientsQueries) : ClientDAO {
    val mapper = ClientMapper()

    override fun insertClient(client: Client): Unit =
        clientsQueries.insertClient(client.userId, client.id)

    override fun insertClients(clients: List<Client>) = clientsQueries.transaction {
        clients.forEach { client ->
            clientsQueries.insertClient(client.userId, client.id)
        }
    }

    override fun getClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity): List<Client> =
        clientsQueries.selectAllClientsByUserId(qualifiedID).executeAsList().map(mapper::toModel)

    override fun deleteClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity): Unit = clientsQueries.deleteClientsOfUser(qualifiedID)

    override fun deleteClient(userId: QualifiedIDEntity, clientId: String) = clientsQueries.deleteClient(userId, clientId)
    
}
