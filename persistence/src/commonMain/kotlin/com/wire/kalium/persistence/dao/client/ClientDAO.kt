package com.wire.kalium.persistence.dao.client

import com.wire.kalium.persistence.dao.QualifiedID
import kotlinx.coroutines.flow.Flow

data class Client(
    val userId: QualifiedID,
    val id: String
)

interface ClientDAO {
    suspend fun insertClient(client: Client)
    suspend fun insertClients(clients: List<Client>)
    suspend fun getClientsOfUserByQualifiedID(qualifiedID: QualifiedID): Flow<List<Client>>
    suspend fun deleteClientsOfUserByQualifiedID(qualifiedID: QualifiedID)
    suspend fun deleteClient(userId: QualifiedID, clientId: String)
}
