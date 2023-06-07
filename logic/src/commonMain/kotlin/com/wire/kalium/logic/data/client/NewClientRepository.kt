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
package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.daokaliumdb.GlobalMetadataDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface NewClientRepository {
    suspend fun saveNewClientEvent(newClientEvent: Event.User.NewClient, userId: UserId)
    suspend fun clearNewClientsForUser(userId: UserId)
    suspend fun clearNewClients()
    suspend fun observeNewClients(): Flow<Either<StorageFailure, List<Pair<Client, UserId>>>>
}

@Suppress("INAPPLICABLE_JVM_NAME")
class NewClientDataSource(
    private val globalMetadataDAO: GlobalMetadataDAO,
    private val clientMapper: ClientMapper = MapperProvider.clientMapper(),
) : NewClientRepository {
    override suspend fun saveNewClientEvent(newClientEvent: Event.User.NewClient, userId: UserId) {
        val prevList = getNewClientsList()

        val newListString = Json.encodeToString(prevList.plus(clientMapper.fromNewClientEvent(newClientEvent) to userId))
        globalMetadataDAO.insertValue(newListString, NEW_CLIENTS_LIST_KEY)
    }

    override suspend fun clearNewClientsForUser(userId: UserId) {
        val prevList = getNewClientsList()

        val newListString = Json.encodeToString(prevList.filter { it.second != userId })
        globalMetadataDAO.insertValue(newListString, NEW_CLIENTS_LIST_KEY)
    }

    override suspend fun clearNewClients() {
        globalMetadataDAO.insertValue("", NEW_CLIENTS_LIST_KEY)
    }

    override suspend fun observeNewClients(): Flow<Either<StorageFailure, List<Pair<Client, UserId>>>> =
        globalMetadataDAO.valueByKeyFlow(NEW_CLIENTS_LIST_KEY)
            .map { decodeNewClientsList(it) }
            .wrapStorageRequest()

    private suspend fun getNewClientsList() = decodeNewClientsList(globalMetadataDAO.valueByKey(NEW_CLIENTS_LIST_KEY))

    private fun decodeNewClientsList(stringValue: String?): List<Pair<Client, UserId>> =
        stringValue?.let {
            try {
                Json.decodeFromString<List<Pair<Client, UserId>>>(it)
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        } ?: listOf()

    companion object {
        const val NEW_CLIENTS_LIST_KEY = "new_clients_list"
    }
}
