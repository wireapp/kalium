package com.wire.kalium.persistence.dao.client

import com.wire.kalium.persistence.dao.QualifiedIDEntity

data class Client(
    val userId: QualifiedIDEntity,
    val id: String
)

interface ClientDAO {
    fun insertClient(client: Client)
    fun insertClients(clients: List<Client>)
    fun getClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity): List<Client>
    fun deleteClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity)
    fun deleteClient(userId: QualifiedIDEntity, clientId: String)
}
