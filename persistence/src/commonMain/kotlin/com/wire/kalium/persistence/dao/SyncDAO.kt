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
package com.wire.kalium.persistence.dao

import app.cash.sqldelight.ExecutableQuery
import com.wire.kalium.persistence.SyncQueries
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

interface SyncDAO {

    /**
     * @return [List] of [UserIDEntity] of all other users.
     * the list does not contain self user ID
     */
    suspend fun allOtherUsersId(): List<UserIDEntity>
}

internal class SyncDAOImpl internal constructor(
    private val syncQueries: SyncQueries,
    private val coroutineContext: CoroutineContext
) : SyncDAO {
    override suspend fun allOtherUsersId(): List<UserIDEntity> = withContext(coroutineContext) {
        syncQueries.userIdsWithOutSelf().executeAsList()
    }
}
