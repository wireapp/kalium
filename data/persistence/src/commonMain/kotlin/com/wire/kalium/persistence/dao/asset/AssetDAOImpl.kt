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

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.AssetsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

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
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
    private val mapper: AssetMapper = AssetMapper
) : AssetDAO {

    // TODO(federation): support the case where domain is null
    override suspend fun insertAsset(assetEntity: AssetEntity) {
        withContext(writeDispatcher.value) {
            queries.insertAsset(
                assetEntity.key,
                assetEntity.domain.orEmpty(),
                assetEntity.dataPath,
                assetEntity.dataSize,
                assetEntity.downloadedDate
            )
        }
    }

    override suspend fun insertAssets(assetsEntity: List<AssetEntity>) = withContext(writeDispatcher.value) {
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

    override fun getAssetByKey(assetKey: String): Flow<AssetEntity?> {
        return queries.selectByKey(assetKey, mapper::fromAssets)
            .asFlow()
            .mapToOneOrNull()
            .flowOn(readDispatcher.value)
    }

    override suspend fun updateAsset(assetEntity: AssetEntity) {
        withContext(writeDispatcher.value) {
            queries.updateAsset(
                assetEntity.downloadedDate,
                assetEntity.dataPath,
                assetEntity.dataSize,
                assetEntity.key
            )
        }
    }

    override suspend fun deleteAsset(key: String) {
        withContext(writeDispatcher.value) {
            queries.deleteAsset(key)
        }
    }

    override suspend fun getAssets(): List<AssetEntity> = withContext(readDispatcher.value) {
        queries.getAssets(mapper::fromAssets).awaitAsList()
    }

    override suspend fun isFileReferenced(path: String): Boolean = withContext(readDispatcher.value) {
        queries.isFileReferenced(path).awaitAsOne()
    }

    override suspend fun deleteMessageFiles(conversationId: QualifiedIDEntity, messageId: String?, deleteFile: (String) -> Unit) =
        deleteFiles(conversationId, messageId, includeDrafts = false, deleteFile)

    override suspend fun deleteConversationFiles(conversationId: QualifiedIDEntity, deleteFile: (String) -> Unit) =
        deleteFiles(conversationId, messageId = null, includeDrafts = true, deleteFile)

    private suspend fun deleteFiles(
        conversationId: QualifiedIDEntity,
        messageId: String?,
        includeDrafts: Boolean,
        deleteFile: (String) -> Unit,
    ) =
        withContext(writeDispatcher.value) {
            queries.transaction {
                // Ordinary text/system deletion should not materialize all of the account's file references.
                val drafts = if (includeDrafts) 1L else 0L
                if (!queries.messageHasLocalFiles(conversationId, messageId, drafts).awaitAsOne()) return@transaction
                val paths = queries.messageFilesToDelete(
                    conversationId = conversationId,
                    messageId = messageId,
                    includeDrafts = drafts
                ).awaitAsList()
                paths.forEach { path ->
                    currentCoroutineContext().ensureActive()
                    deleteFile(path)
                }
                // A partial filesystem failure keeps every path available for retry. Missing files are accepted by the caller.
                paths.forEach { queries.forgetDeletedMessageFile(it).await() }
            }
        }
}
