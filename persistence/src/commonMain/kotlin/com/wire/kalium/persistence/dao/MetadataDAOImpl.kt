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

package com.wire.kalium.persistence.dao

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.MetadataQueries
import com.wire.kalium.persistence.cache.FlowCache
import com.wire.kalium.persistence.util.JsonSerializer
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlin.coroutines.CoroutineContext

class MetadataDAOImpl internal constructor(
    private val metadataQueries: MetadataQueries,
    private val metadataCache: FlowCache<String, String?>,
    private val databaseScope: CoroutineScope,
    private val queriesContext: CoroutineContext
) : MetadataDAO {

    override suspend fun insertValue(value: String, key: String) = withContext(queriesContext) {
        metadataQueries.insertValue(key, value)
    }

    override suspend fun deleteValue(key: String) = withContext(queriesContext) {
        metadataQueries.deleteValue(key)
    }

    override suspend fun valueByKeyFlow(
        key: String
    ): Flow<String?> = metadataCache.get(key) {
        metadataQueries.selectValueByKey(key)
            .asFlow()
            .mapToOneOrNull()
    }

    override suspend fun valueByKey(key: String): String? = valueByKeyFlow(key).first()

    override suspend fun clear(keysToKeep: List<String>?) = withContext(queriesContext) {
        if (keysToKeep == null) {
            metadataQueries.deleteAll()
        } else {
            metadataQueries.deleteAllExcept(keysToKeep)
        }
    }

    override suspend fun <T> putSerializable(key: String, value: T, kSerializer: KSerializer<T>) {
        val jsonString = JsonSerializer().encodeToString(kSerializer, value)
        insertValue(value = jsonString, key = key)
    }

    override suspend fun <T> getSerializable(key: String, kSerializer: KSerializer<T>): T? {
        val jsonString: String? = valueByKey(key)
        return jsonString?.let {
            JsonSerializer().decodeFromString(kSerializer, it)
        }
    }

    override fun <T> observeSerializable(key: String, kSerializer: KSerializer<T>): Flow<T?> {
        // TODO: Cache
        return metadataQueries.selectValueByKey(key)
            .asFlow()
            .mapToOneOrNull()
            .map { jsonString ->
                jsonString?.let {
                    JsonSerializer().decodeFromString(kSerializer, it)
                }
            }
            .distinctUntilChanged()
            .shareIn(databaseScope, SharingStarted.Lazily, 1)
    }
}
