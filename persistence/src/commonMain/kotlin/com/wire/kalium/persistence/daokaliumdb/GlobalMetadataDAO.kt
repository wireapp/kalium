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
package com.wire.kalium.persistence.daokaliumdb

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.GlobalMetadataQueries
import com.wire.kalium.persistence.cache.Cache
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

interface GlobalMetadataDAO {
    suspend fun insertValue(value: String, key: String)
    suspend fun deleteValue(key: String)
    suspend fun valueByKeyFlow(key: String): Flow<String?>
    suspend fun valueByKey(key: String): String?
    suspend fun clear(keysToKeep: List<String>?)
}

class GlobalMetadataDAOImpl internal constructor(
    private val queries: GlobalMetadataQueries,
    private val metadataCache: Cache<String, Flow<String?>>,
    private val databaseScope: CoroutineScope,
    private val queriesContext: CoroutineContext
) : GlobalMetadataDAO {

    override suspend fun insertValue(value: String, key: String) = withContext(queriesContext) {
        queries.insertValue(key, value)
    }

    override suspend fun deleteValue(key: String) {
        queries.deleteValue(key)
    }

    override suspend fun valueByKeyFlow(key: String): Flow<String?> = metadataCache.get(key) {
        queries.selectValueByKey(key)
            .asFlow()
            .mapToOneOrNull()
            .distinctUntilChanged()
            .shareIn(databaseScope, SharingStarted.Lazily, 1)
    }

    override suspend fun valueByKey(key: String): String? = withContext(queriesContext) {
        queries.selectValueByKey(key).executeAsOneOrNull()
    }

    override suspend fun clear(keysToKeep: List<String>?) = withContext(queriesContext) {
        if (keysToKeep == null) {
            queries.deleteAll()
        } else {
            queries.deleteAllExcept(keysToKeep)
        }
    }
}

