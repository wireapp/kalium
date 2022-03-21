package com.wire.kalium.persistence.dao.client

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow

data class Client(
    val userId: QualifiedIDEntity,
    val id: String
)

interface ClientDAO {
    suspend fun insertClient(client: Client)
    suspend fun insertClients(clients: List<Client>)
    suspend fun getClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity): Flow<List<Client>>
    suspend fun deleteClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity)
    suspend fun deleteClient(userId: QualifiedIDEntity, clientId: String)
}
