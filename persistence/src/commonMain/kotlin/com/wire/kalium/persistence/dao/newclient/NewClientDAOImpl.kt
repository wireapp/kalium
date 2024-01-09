/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.persistence.dao.newclient

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.NewClientQueries
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.persistence.dao.client.InsertClientParam
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext

internal object NewClientMapper {
    @Suppress("FunctionParameterNaming", "LongParameterList")
    fun fromClient(
        id: String,
        device_type: DeviceTypeEntity?,
        registration_date: Instant?,
        model: String?,
    ): NewClientEntity = NewClientEntity(
        id = id,
        deviceType = device_type,
        registrationDate = registration_date,
        model = model
    )
}

internal class NewClientDAOImpl(
    private val newClientsQueries: NewClientQueries,
    private val queriesContext: CoroutineContext,
    private val mapper: NewClientMapper = NewClientMapper
) : NewClientDAO {

    override suspend fun insertNewClient(client: InsertClientParam) = with(client) {
        withContext(queriesContext) {
            newClientsQueries.insertNewClient(
                user_id = userId,
                id = id,
                device_type = deviceType,
                client_type = clientType,
                is_valid = true,
                is_mls_capable = isMLSCapable,
                registration_date = registrationDate,
                last_active = lastActive,
                model = model,
                label = label,
                mls_public_keys = mlsPublicKeys
            )
        }
    }

    override suspend fun observeNewClients(): Flow<List<NewClientEntity>> =
        newClientsQueries.selectNewClientsForUser(mapper::fromClient)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()

    override suspend fun clearNewClients() = withContext(queriesContext) {
        newClientsQueries.clearNewClients()
    }
}
