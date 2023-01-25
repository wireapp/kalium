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
    suspend fun conversationRecipient(ids: QualifiedIDEntity): Map<QualifiedIDEntity, List<Client>>
    suspend fun insertClientsAndRemoveRedundant(clients: List<InsertClientParam>)
    suspend fun tryMarkInvalid(invalidClientsList: List<Pair<QualifiedIDEntity, List<String>>>)
}
