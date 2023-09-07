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
package com.wire.kalium.logic.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapStorageNullableRequest
import com.wire.kalium.persistence.datastore.USERS_LOGGED_IN
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface AccessRepository {
    suspend fun loggedInUsers(): Either<StorageFailure, Int?>

    suspend fun markUserAsLoggedIn()

    suspend fun markUserAsLoggedOut()
}

class AccessRepositoryDataSource(
    private val dataStore: DataStore<Preferences>
) : AccessRepository {
    override suspend fun loggedInUsers(): Either<StorageFailure, Int?> = wrapStorageNullableRequest {
        dataStore.data.map { it[USERS_LOGGED_IN] }.first()
    }

    override suspend fun markUserAsLoggedIn() {
        dataStore.edit {
            it[USERS_LOGGED_IN] = (it[USERS_LOGGED_IN] ?: 0) + 1
        }
    }

    override suspend fun markUserAsLoggedOut() {
        dataStore.edit {
            it[USERS_LOGGED_IN] = (it[USERS_LOGGED_IN] ?: 0) - 1
        }
    }
}
