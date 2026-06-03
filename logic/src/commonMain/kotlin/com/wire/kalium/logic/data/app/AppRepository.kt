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
package com.wire.kalium.logic.data.app

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapFlowStorageRequest
import com.wire.kalium.common.error.wrapNullableFlowStorageRequest
import com.wire.kalium.common.error.wrapStorageNullableRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.AppDAO
import com.wire.kalium.persistence.dao.TeamDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlin.collections.map

internal interface AppRepository {
    fun observeAllApps(): Flow<Either<StorageFailure, List<AppDetails>>>
    fun searchAppsByName(name: String): Flow<Either<StorageFailure, List<AppDetails>>>
    suspend fun getAppById(appId: QualifiedID): Either<StorageFailure, AppDetails?>
    fun observeIsAppMember(
        appId: QualifiedID,
        conversationId: ConversationId
    ): Flow<Either<StorageFailure, UserId?>>
}

internal class AppDataSource internal constructor(
    private val appDAO: AppDAO,
    private val teamDAO: TeamDAO,
    private val appMapper: AppMapper = MapperProvider.appMapper()
) : AppRepository {
    override fun observeAllApps(): Flow<Either<StorageFailure, List<AppDetails>>> =
        wrapFlowStorageRequest {
            appDAO.observeAllApps().map { apps ->
                apps.map { app ->
                    appMapper.fromDaoToModel(appEntity = app)
                }
            }
        }

    override fun searchAppsByName(name: String): Flow<Either<StorageFailure, List<AppDetails>>> =
        wrapFlowStorageRequest {
            appDAO.searchAppsByName(query = name).map { apps ->
                apps.map { app ->
                    appMapper.fromDaoToModel(appEntity = app)
                }
            }
        }

    override suspend fun getAppById(appId: QualifiedID): Either<StorageFailure, AppDetails?> =
        wrapStorageNullableRequest {
            appDAO.byId(id = appId.toDao())
                ?.let { appEntity ->
                    val creator = appEntity.teamId?.let { teamDAO.getTeamById(it).firstOrNull()?.name }
                    appMapper.fromDaoToModel(appEntity = appEntity, creator = creator)
                }
        }

    override fun observeIsAppMember(
        appId: QualifiedID,
        conversationId: ConversationId
    ): Flow<Either<StorageFailure, UserId?>> = wrapNullableFlowStorageRequest {
        appDAO.observeIsAppMember(
            appId = appId.toDao(),
            conversationId = conversationId.toDao()
        ).map { it?.toModel() }
    }
}
