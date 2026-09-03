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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapFlowStorageRequest
import com.wire.kalium.common.error.wrapNullableFlowStorageRequest
import com.wire.kalium.common.error.wrapStorageNullableRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.api.model.UserTypeDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.persistence.dao.AppDAO
import com.wire.kalium.persistence.dao.TeamDAO
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlin.collections.map

internal interface AppRepository {
    suspend fun syncApps(teamId: TeamId, includeTeamApps: Boolean): Either<CoreFailure, Unit>
    fun observeAllApps(): Flow<Either<StorageFailure, List<AppDetails>>>
    fun searchAppsByName(name: String): Flow<Either<StorageFailure, List<AppDetails>>>
    suspend fun getAppById(appId: QualifiedID): Either<StorageFailure, AppDetails?>
    fun observeIsAppMember(
        appId: QualifiedID,
        conversationId: ConversationId
    ): Flow<Either<StorageFailure, UserId?>>
}

internal class AppDataSource internal constructor(
    private val selfUserId: UserId,
    private val appDAO: AppDAO,
    private val teamDAO: TeamDAO,
    private val teamsApi: TeamsApi,
    private val userDetailsApi: UserDetailsApi,
    private val appMapper: AppMapper = MapperProvider.appMapper()
) : AppRepository {

    override suspend fun syncApps(teamId: TeamId, includeTeamApps: Boolean): Either<CoreFailure, Unit> =
        fetchTeamApps(teamId, includeTeamApps).flatMap { teamApps ->
            fetchCollaborators(teamId).flatMap { collaboratorIds ->
                val teamAppIds = teamApps.mapTo(mutableSetOf()) { it.id.value }
                val qualifiedCollaboratorIds = collaboratorIds
                    .asSequence()
                    .filterNot(teamAppIds::contains)
                    .distinct()
                    .map { collaboratorId -> UserId(collaboratorId, selfUserId.domain) }
                    .toList()

                fetchCollaboratorProfiles(qualifiedCollaboratorIds).flatMap { collaboratorProfiles ->
                    val apps = (teamApps + collaboratorProfiles.filter { it.type == UserTypeDTO.APP })
                        .distinctBy { it.id }
                        .map(appMapper::fromUserProfileToAppEntity)
                    wrapStorageRequest { appDAO.upsertApps(apps) }
                }
            }
        }

    private suspend fun fetchTeamApps(teamId: TeamId, includeTeamApps: Boolean): Either<CoreFailure, List<UserProfileDTO>> =
        if (includeTeamApps) {
            wrapApiRequest { teamsApi.getTeamApps(teamId.value) }
        } else {
            Either.Right(emptyList())
        }

    private suspend fun fetchCollaborators(teamId: TeamId): Either<CoreFailure, List<String>> =
        wrapApiRequest { teamsApi.getTeamCollaborators(teamId.value) }
            .map { collaborators -> collaborators.map { it.nonQualifiedUserId } }
            .flatMapLeft { failure ->
                if (failure.isInsufficientPermissions()) Either.Right(emptyList()) else Either.Left(failure)
            }

    private suspend fun fetchCollaboratorProfiles(collaboratorIds: List<UserId>): Either<CoreFailure, List<UserProfileDTO>> =
        collaboratorIds.chunked(USER_BATCH_SIZE).foldToEitherWhileRight(emptyList()) { ids, profiles ->
            wrapApiRequest {
                userDetailsApi.getMultipleUsers(ListUserRequest.qualifiedIds(ids.map { it.toApi() }))
            }.map { response -> profiles + response.usersFound }
        }

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

    private companion object {
        const val USER_BATCH_SIZE = 500
    }
}

private fun CoreFailure.isInsufficientPermissions(): Boolean {
    val error = (this as? NetworkFailure.ServerMiscommunication)
        ?.kaliumException as? KaliumException.InvalidRequestError
    return error?.errorResponse?.code == HttpStatusCode.Forbidden.value &&
        error.errorResponse.label == INSUFFICIENT_PERMISSIONS_LABEL
}

private const val INSUFFICIENT_PERMISSIONS_LABEL = "insufficient-permissions"
