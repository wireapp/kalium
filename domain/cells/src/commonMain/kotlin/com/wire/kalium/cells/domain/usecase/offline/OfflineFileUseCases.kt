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
package com.wire.kalium.cells.domain.usecase.offline

import com.wire.kalium.persistence.dao.cellfile.CellFileDao
import com.wire.kalium.persistence.dao.cellfile.CellFileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public data class OfflineFileInfo(
    val id: String,
    val name: String,
    val owner: String,
    val localPath: String,
    val size: Long?,
    val downloadedAt: Long,
)

/**
 * Use case for saving an offline file's information to the local database.
 */
public interface SaveOfflineFileUseCase {
    public suspend operator fun invoke(info: OfflineFileInfo)
}

/**
 * Use case for deleting an offline file's information from the local database by its ID.
 */
public interface DeleteOfflineFileUseCase {
    public suspend operator fun invoke(id: String)
}

/**
 * Use case for observing the list of offline files stored in the local database.
 * Returns a flow that emits the current list of [OfflineFileInfo] whenever it changes.
 */
public interface ObserveOfflineFilesUseCase {
    public operator fun invoke(): Flow<List<OfflineFileInfo>>
}

/**
 * Use case for retrieving a specific offline file's information from the local database by its ID.
 * Returns the [OfflineFileInfo] if found, or null if no file with the given ID exists.
 */
public interface GetOfflineFileUseCase {
    public suspend operator fun invoke(id: String): OfflineFileInfo?
}

internal class SaveOfflineFileUseCaseImpl(
    private val dao: CellFileDao,
) : SaveOfflineFileUseCase {
    override suspend fun invoke(info: OfflineFileInfo) {
        dao.upsert(info.toCellEntity())
    }
}

internal class DeleteOfflineFileUseCaseImpl(
    private val dao: CellFileDao,
) : DeleteOfflineFileUseCase {
    override suspend fun invoke(id: String) {
        dao.delete(id)
    }
}

internal class ObserveOfflineFilesUseCaseImpl(
    private val dao: CellFileDao,
) : ObserveOfflineFilesUseCase {
    override fun invoke(): Flow<List<OfflineFileInfo>> =
        dao.observeOfflineFiles().map { list -> list.map { it.toInfo() } }
}

internal class GetOfflineFileUseCaseImpl(
    private val dao: CellFileDao,
) : GetOfflineFileUseCase {
    override suspend fun invoke(id: String): OfflineFileInfo? =
        dao.getById(id)?.toInfo()
}

private fun CellFileEntity.toInfo() = OfflineFileInfo(
    id = uuid,
    name = name.orEmpty(),
    owner = owner.orEmpty(),
    localPath = localPath.orEmpty(),
    size = size,
    downloadedAt = downloadedAt,
)

private fun OfflineFileInfo.toCellEntity() = CellFileEntity(
    uuid = id,
    name = name,
    owner = owner,
    localPath = localPath,
    size = size,
    downloadedAt = downloadedAt,
    isOffline = true,
)
