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
package com.wire.kalium.cells.data
import com.wire.kalium.cells.domain.CellFileRepository
import com.wire.kalium.cells.domain.usecase.offline.OfflineFileInfo
import com.wire.kalium.persistence.dao.cellfile.CellFileDao
import com.wire.kalium.persistence.dao.cellfile.CellFileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
internal class CellFileDataSource(
    private val dao: CellFileDao,
) : CellFileRepository {
    override suspend fun upsert(info: OfflineFileInfo) {
        dao.upsert(info.toEntity())
    }
    override suspend fun delete(id: String) {
        dao.delete(id)
    }
    override fun observeOfflineFiles(): Flow<List<OfflineFileInfo>> =
        dao.observeOfflineFiles().map { list -> list.map { it.toInfo() } }
    override suspend fun getById(id: String): OfflineFileInfo? =
        dao.getById(id)?.toInfo()
}
private fun CellFileEntity.toInfo() = OfflineFileInfo(
    id = uuid,
    name = name.orEmpty(),
    owner = owner.orEmpty(),
    localPath = localPath.orEmpty(),
    size = size,
    downloadedAt = downloadedAt,
    modifiedAt = modifiedAt,
    conversationId = conversationId.ifEmpty { null },
)

private fun OfflineFileInfo.toEntity() = CellFileEntity(
    uuid = id,
    name = name,
    owner = owner,
    localPath = localPath,
    size = size,
    downloadedAt = downloadedAt,
    isOffline = true,
    modifiedAt = modifiedAt,
    conversationId = conversationId.orEmpty(),
)
