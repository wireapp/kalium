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

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.MetadataQueries
import com.wire.kalium.persistence.cache.Cache
import com.wire.kalium.persistence.util.JsonSerializer
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlin.coroutines.CoroutineContext

class MetadataDAOImpl internal constructor(
    private val metadataQueries: MetadataQueries,
    private val metadataCache: Cache<String, Flow<String?>>,
    private val databaseScope: CoroutineScope,
    private val queriesContext: CoroutineContext
) : MetadataDAO {

    override suspend fun insertValue(value: String, key: String) = withContext(queriesContext) {
        metadataQueries.insertValue(key, value)
    }

    override suspend fun deleteValue(key: String) {
        metadataQueries.deleteValue(key)
    }

    override suspend fun valueByKeyFlow(key: String): Flow<String?> = metadataCache.get(key) {
        metadataQueries.selectValueByKey(key)
            .asFlow()
            .mapToOneOrNull()
            .distinctUntilChanged()
            .shareIn(databaseScope, SharingStarted.Lazily, 1)
    }

    override suspend fun valueByKey(key: String): String? = withContext(queriesContext) {
        metadataQueries.selectValueByKey(key).executeAsOneOrNull()
    }

    override suspend fun <T> insertSerializable(key: String, value: T, kSerializer: KSerializer<T>) {
        metadataQueries.insertValue(key, JsonSerializer().encodeToString(kSerializer, value))
    }

    override suspend fun <T> getSerializable(key: String, kSerializer: KSerializer<T>): T? {
        val jsonString: String? = metadataQueries.selectValueByKey(key).executeAsOneOrNull()
        return jsonString?.let {
            JsonSerializer().decodeFromString(kSerializer, it)
        } ?: run { null }
    }

    override suspend fun <T> getSerializableFlow(key: String, kSerializer: KSerializer<T>): Flow<T?> {
        return flowOf(getSerializable(key, kSerializer)).distinctUntilChanged().shareIn(databaseScope, SharingStarted.Lazily, 1)
    }
}
