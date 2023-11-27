/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.team

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.conversation.LegalHoldStatusMapper
import com.wire.kalium.logic.data.event.EventMapper
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.service.ServiceMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandler
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.base.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.ServiceDAO
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.message.LocalId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TeamRepository {
    suspend fun fetchTeamById(teamId: TeamId): Either<CoreFailure, Team>
    suspend fun getTeam(teamId: TeamId): Flow<Team?>
    suspend fun deleteConversation(conversationId: ConversationId, teamId: TeamId): Either<CoreFailure, Unit>
    suspend fun updateMemberRole(teamId: String, userId: String, permissionCode: Int?): Either<CoreFailure, Unit>
    suspend fun fetchTeamMember(teamId: String, userId: String): Either<CoreFailure, Unit>
    suspend fun removeTeamMember(teamId: String, userId: String): Either<CoreFailure, Unit>
    suspend fun updateTeam(team: Team): Either<CoreFailure, Unit>
    suspend fun syncServices(teamId: TeamId): Either<CoreFailure, Unit>
    suspend fun approveLegalHold(teamId: TeamId, password: String?): Either<CoreFailure, Unit>
    suspend fun fetchLegalHoldStatus(teamId: TeamId): Either<CoreFailure, LegalHoldStatus>
}

@Suppress("LongParameterList")
internal class TeamDataSource(
    private val userDAO: UserDAO,
    private val teamDAO: TeamDAO,
    private val teamsApi: TeamsApi,
    private val userDetailsApi: UserDetailsApi,
    private val selfUserId: UserId,
    private val serviceDAO: ServiceDAO,
    private val legalHoldHandler: LegalHoldHandler,
    private val legalHoldRequestHandler: LegalHoldRequestHandler,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val teamMapper: TeamMapper = MapperProvider.teamMapper(),
    private val serviceMapper: ServiceMapper = MapperProvider.serviceMapper(),
    private val userTypeEntityTypeMapper: UserEntityTypeMapper = MapperProvider.userTypeEntityMapper(),
    private val legalHoldStatusMapper: LegalHoldStatusMapper = MapperProvider.legalHoldStatusMapper(),
    private val eventMapper: EventMapper = MapperProvider.eventMapper(selfUserId),
) : TeamRepository {

    override suspend fun fetchTeamById(teamId: TeamId): Either<CoreFailure, Team> = wrapApiRequest {
        teamsApi.getTeamInfo(teamId = teamId.value)
    }.map { teamDTO ->
        teamMapper.fromDtoToEntity(teamDTO)
    }.flatMap { teamEntity ->
        wrapStorageRequest { teamDAO.insertTeam(team = teamEntity) }.map {
            teamMapper.fromDaoModelToTeam(teamEntity)
        }
    }

    override suspend fun getTeam(teamId: TeamId): Flow<Team?> =
        teamDAO.getTeamById(teamId.value)
            .map {
                it?.let {
                    teamMapper.fromDaoModelToTeam(it)
                }
            }

    override suspend fun deleteConversation(conversationId: ConversationId, teamId: TeamId): Either<CoreFailure, Unit> {
        return wrapApiRequest {
            teamsApi.deleteConversation(conversationId.value, teamId.value)
        }
    }

    override suspend fun updateMemberRole(teamId: String, userId: String, permissionCode: Int?): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            userDAO.upsertTeamMemberUserTypes(
                mapOf(
                    QualifiedIDEntity(userId, selfUserId.domain) to userTypeEntityTypeMapper.teamRoleCodeToUserType(permissionCode)
                )
            )
        }
    }

    override suspend fun fetchTeamMember(teamId: String, userId: String): Either<CoreFailure, Unit> {
        return wrapApiRequest {
            teamsApi.getTeamMember(
                teamId = teamId,
                userId = userId,
            )
        }.flatMap { member ->
            wrapApiRequest { userDetailsApi.getUserInfo(userId = QualifiedID(userId, selfUserId.domain)) }
                .flatMap { userProfileDTO ->
                    wrapStorageRequest {
                        val userEntity = userMapper.fromUserProfileDtoToUserEntity(
                            userProfile = userProfileDTO,
                            connectionState = ConnectionEntity.State.ACCEPTED,
                            userTypeEntity = userTypeEntityTypeMapper.teamRoleCodeToUserType(member.permissions?.own)
                        )
                        userDAO.upsertUser(userEntity)
                        userDAO.upsertConnectionStatuses(mapOf(userEntity.id to userEntity.connectionStatus))
                    }
                }
        }
    }

    override suspend fun removeTeamMember(teamId: String, userId: String): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            userDAO.markUserAsDeleted(QualifiedIDEntity(userId, selfUserId.domain))
        }
    }

    override suspend fun updateTeam(team: Team): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            teamDAO.updateTeam(teamMapper.fromModelToEntity(team))
        }
    }

    // TODO: there is no documentation about getting the next page in case there is a next page
    override suspend fun syncServices(teamId: TeamId): Either<CoreFailure, Unit> = wrapApiRequest {
        teamsApi.whiteListedServices(teamId = teamId.value)
    }.map {
        it.services.map { service ->
            serviceMapper.mapToServiceEntity(service, selfUserId)
        }
    }.flatMap {
        wrapStorageRequest {
            serviceDAO.insertMultiple(it)
        }
    }

    override suspend fun approveLegalHold(teamId: TeamId, password: String?): Either<CoreFailure, Unit> = wrapApiRequest {
        teamsApi.approveLegalHold(teamId.value, selfUserId.value, password)
        // TODO: should we update the legal hold status for the current user in the database?
    }

    override suspend fun fetchLegalHoldStatus(teamId: TeamId): Either<CoreFailure, LegalHoldStatus> = wrapApiRequest {
        teamsApi.fetchLegalHoldStatus(teamId.value, selfUserId.value)
    }.flatMap { response ->
        when (response.legalHoldStatusDTO) {
            LegalHoldStatusDTO.ENABLED -> legalHoldHandler.handleEnable(
                eventMapper.legalHoldEnabled(
                    id = LocalId.generate(),
                    transient = true,
                    live = false,
                    eventContentDTO = EventContentDTO.User.LegalHoldEnabledDTO(id = selfUserId.toString())
                )
            )
            LegalHoldStatusDTO.DISABLED -> legalHoldHandler.handleDisable(
                eventMapper.legalHoldDisabled(
                    id = LocalId.generate(),
                    transient = true,
                    live = false,
                    eventContentDTO = EventContentDTO.User.LegalHoldDisabledDTO(id = selfUserId.toString())
                )
            )
            LegalHoldStatusDTO.PENDING ->
                legalHoldRequestHandler.handle(
                    eventMapper.legalHoldRequest(
                        id = LocalId.generate(),
                        transient = true,
                        live = false,
                        eventContentDTO = EventContentDTO.User.NewLegalHoldRequestDTO(
                            clientId = response.clientId!!,
                            lastPreKey = response.lastPreKey!!,
                            id = selfUserId.toString()
                        )
                    )
                )
            LegalHoldStatusDTO.NO_CONSENT -> Either.Right(Unit)
        }.map { legalHoldStatusMapper.fromApiModel(response.legalHoldStatusDTO) }
    }
}
