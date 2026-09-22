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

package com.wire.kalium.persistence.dao.asset

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow

data class AssetEntity(
    val key: String,
    val domain: String?,
    val dataPath: String,
    val dataSize: Long,
    val assetToken: String? = null,
    val downloadedDate: Long?
)

interface AssetDAO {
    suspend fun insertAsset(assetEntity: AssetEntity)
    suspend fun insertAssets(assetsEntity: List<AssetEntity>)
    fun getAssetByKey(assetKey: String): Flow<AssetEntity?>
    suspend fun updateAsset(assetEntity: AssetEntity)
    suspend fun deleteAsset(key: String)
    suspend fun getAssets(): List<AssetEntity>
    suspend fun isFileReferenced(path: String): Boolean

    /** Null messageId selects the conversation's messages. Independent/shared file references survive. */
    suspend fun deleteMessageFiles(conversationId: QualifiedIDEntity, messageId: String?, deleteFile: (String) -> Unit)

    /** Includes drafts owned by a conversation which is being removed. */
    suspend fun deleteConversationFiles(conversationId: QualifiedIDEntity, deleteFile: (String) -> Unit)
}
