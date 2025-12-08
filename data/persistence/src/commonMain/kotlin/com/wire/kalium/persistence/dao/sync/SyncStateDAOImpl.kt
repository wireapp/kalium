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

package com.wire.kalium.persistence.dao.sync

import com.wire.kalium.persistence.SyncOutboxQueries
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

internal class SyncStateDAOImpl internal constructor(
    private val syncOutboxQueries: SyncOutboxQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher
) : SyncStateDAO {

    override suspend fun upsertState(key: String, value: String, updatedAt: Instant) {
        withContext(writeDispatcher.value) {
            syncOutboxQueries.upsertState(key, value, updatedAt)
        }
    }

    override suspend fun selectState(key: String): String? = withContext(readDispatcher.value) {
        syncOutboxQueries.selectState(key).executeAsOneOrNull()
    }
}
