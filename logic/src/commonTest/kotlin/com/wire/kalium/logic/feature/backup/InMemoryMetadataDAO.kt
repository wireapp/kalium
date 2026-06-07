/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.backup

import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal class InMemoryMetadataDAO : MetadataDAO {
    private val values = mutableMapOf<String, String>()

    override suspend fun insertValue(value: String, key: String) {
        values[key] = value
    }

    override suspend fun deleteValue(key: String) {
        values.remove(key)
    }

    override fun valueByKeyFlow(key: String): Flow<String?> = flowOf(values[key])

    override suspend fun valueByKey(key: String): String? = values[key]

    override suspend fun clear(keysToKeep: List<String>?) {
        if (keysToKeep == null) {
            values.clear()
        } else {
            values.keys.removeAll { it !in keysToKeep }
        }
    }

    override suspend fun <T> putSerializable(key: String, value: T, kSerializer: KSerializer<T>) {
        values[key] = Json.encodeToString(kSerializer, value)
    }

    override suspend fun <T> getSerializable(key: String, kSerializer: KSerializer<T>): T? =
        values[key]?.let { Json.decodeFromString(kSerializer, it) }

    override fun <T> observeSerializable(key: String, kSerializer: KSerializer<T>): Flow<T?> =
        flowOf(values[key]?.let { Json.decodeFromString(kSerializer, it) })
}
