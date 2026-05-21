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

import kotlinx.coroutines.flow.Flow

data class CellFileEntity(
    val uuid: String,
    val conversationId: String,
    val name: String?,
    val owner: String?,
    val mimeType: String? = null,
    val localPath: String?,
    val size: Long?,
    val downloadedAt: Long,
    val isOffline: Boolean,
    val modifiedAt: Long? = null,
)

data class CellFileLocalPath(
    val uuid: String,
    val localPath: String,
)

interface CellFileDao {
    suspend fun upsert(entity: CellFileEntity)
    suspend fun setTransferStatus(id: String, status: String)
    suspend fun delete(id: String)
    fun observeOfflineFiles(): Flow<List<CellFileEntity>>
    fun observeOfflineFilesByConversationId(conversationId: String): Flow<List<CellFileEntity>>
    suspend fun getById(id: String): CellFileEntity?
    suspend fun getAllWithLocalPath(): List<CellFileLocalPath>
}
