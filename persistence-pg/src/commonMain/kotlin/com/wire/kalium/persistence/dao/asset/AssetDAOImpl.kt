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

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.AssetsQueries
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal object AssetMapper {
    @Suppress("FunctionParameterNaming")
    fun fromAssets(
        key: String,
        domain: String?,
        data_path: String,
        data_size: Long,
        downloaded_date: Long?
    ): AssetEntity {
        return AssetEntity(
            key = key,
            domain = domain,
            dataPath = data_path,
            dataSize = data_size,
            downloadedDate = downloaded_date
        )
    }
}

class AssetDAOImpl internal constructor(
    private val queries: AssetsQueries,
    private val queriesContext: CoroutineContext,
    private val mapper: AssetMapper = AssetMapper
) : AssetDAO {

    // TODO(federation): support the case where domain is null
    override suspend fun insertAsset(assetEntity: AssetEntity) = withContext(queriesContext) {
        queries.insertAsset(
            assetEntity.key,
            assetEntity.domain.orEmpty(),
            assetEntity.dataPath,
            assetEntity.dataSize,
            assetEntity.downloadedDate
        )
    }

    override suspend fun insertAssets(assetsEntity: List<AssetEntity>) = withContext(queriesContext) {
        queries.transaction {
            assetsEntity.forEach { asset ->
                queries.insertAsset(
                    asset.key,
                    asset.domain.orEmpty(),
                    asset.dataPath,
                    asset.dataSize,
                    asset.downloadedDate
                )
            }
        }
    }

    override suspend fun getAssetByKey(assetKey: String): Flow<AssetEntity?> {
        return queries.selectByKey(assetKey, mapper::fromAssets)
            .asFlow()
            .flowOn(queriesContext)
            .mapToOneOrNull()
    }

    override suspend fun updateAsset(assetEntity: AssetEntity) = withContext(queriesContext) {
        queries.updateAsset(
            assetEntity.downloadedDate,
            assetEntity.dataPath,
            assetEntity.dataSize,
            assetEntity.key
        )
    }

    override suspend fun deleteAsset(key: String) = withContext(queriesContext) {
        queries.deleteAsset(key)
    }
}
