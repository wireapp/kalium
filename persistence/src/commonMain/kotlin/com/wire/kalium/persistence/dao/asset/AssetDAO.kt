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

import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow

data class AssetEntity(
    val key: String,
    val domain: String?,
    val dataPath: String,
    val dataSize: Long,
    val assetToken: String? = null,
    val downloadedDate: Long?
)

@Mockable
interface AssetDAO {
    suspend fun insertAsset(assetEntity: AssetEntity)
    suspend fun insertAssets(assetsEntity: List<AssetEntity>)
    suspend fun getAssetByKey(assetKey: String): Flow<AssetEntity?>
    suspend fun updateAsset(assetEntity: AssetEntity)
    suspend fun deleteAsset(key: String)
    suspend fun getAssets(): List<AssetEntity>
}
