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
package com.wire.kalium.persistence.dao.cellfile

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.CellFilesQueries
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

internal class CellFileDaoImpl(
    private val queries: CellFilesQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : CellFileDao {

    override suspend fun upsert(entity: CellFileEntity) {
        withContext(writeDispatcher.value) {
            queries.upsertCellFile(
                uuid = entity.uuid,
                name = entity.name,
                owner = entity.owner,
                localPath = entity.localPath,
                size = entity.size,
                downloadedAt = entity.downloadedAt,
                isOffline = if (entity.isOffline) 1L else 0L,
            )
        }
    }

    override suspend fun delete(id: String) {
        withContext(writeDispatcher.value) {
            queries.deleteCellFile(id)
        }
    }

    override fun observeOfflineFiles(): Flow<List<CellFileEntity>> =
        queries.observeOfflineFiles(::toEntity)
            .asFlow()
            .mapToList()
            .flowOn(readDispatcher.value)

    override suspend fun getById(id: String): CellFileEntity? = withContext(readDispatcher.value) {
        queries.selectById(id, ::toEntity).executeAsOneOrNull()
    }

    override suspend fun getAllWithLocalPath(): List<Pair<String, String>> = withContext(readDispatcher.value) {
        queries.getAllWithLocalPath { uuid, localPath -> uuid to localPath!! }.executeAsList()
    }

    @Suppress("LongParameterList")
    private fun toEntity(
        uuid: String,
        name: String?,
        owner: String?,
        localPath: String?,
        size: Long?,
        downloadedAt: Long,
        isOffline: Long,
    ): CellFileEntity = CellFileEntity(
        uuid = uuid,
        name = name,
        owner = owner,
        localPath = localPath,
        size = size,
        downloadedAt = downloadedAt,
        isOffline = isOffline == 1L,
    )
}
