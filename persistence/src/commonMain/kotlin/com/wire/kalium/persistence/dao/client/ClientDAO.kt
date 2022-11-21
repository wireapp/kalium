package com.wire.kalium.persistence.dao.client

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow

data class Client(
    val userId: QualifiedIDEntity,
    val id: String,
    val deviceType: DeviceTypeEntity?,
    val isValid: Boolean
)

data class InsertClientParam(
    val userId: QualifiedIDEntity,
    val id: String,
    val deviceType: DeviceTypeEntity?
)

enum class DeviceTypeEntity {
    Phone,
    Tablet,
    Desktop,
    LegalHold,
    Unknown;
}

interface ClientDAO {
    suspend fun insertClient(client: InsertClientParam)
    suspend fun insertClients(clients: List<InsertClientParam>)
    suspend fun getClientsOfUserByQualifiedIDFlow(qualifiedID: QualifiedIDEntity): Flow<List<Client>>
    suspend fun getClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity): List<Client>
    suspend fun getClientsOfUsersByQualifiedIDs(ids: List<QualifiedIDEntity>): Map<QualifiedIDEntity, List<Client>>
    suspend fun deleteClientsOfUserByQualifiedID(qualifiedID: QualifiedIDEntity)
    suspend fun deleteClient(userId: QualifiedIDEntity, clientId: String)
    suspend fun getClientsOfConversation(id: QualifiedIDEntity): Map<QualifiedIDEntity, List<Client>>
    suspend fun conversationRepents(ids: QualifiedIDEntity): Map<QualifiedIDEntity, List<Client>>
    suspend fun insertClientsAndRemoveRedundant(clients: List<InsertClientParam>)
    suspend fun tryMarkInvalid(userId: QualifiedIDEntity, clientId: String)
}
