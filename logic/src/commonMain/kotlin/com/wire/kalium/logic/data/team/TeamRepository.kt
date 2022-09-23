package com.wire.kalium.logic.data.team

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TeamRepository {
    suspend fun fetchTeamById(teamId: TeamId): Either<CoreFailure, Team>
    suspend fun fetchMembersByTeamId(teamId: TeamId, userDomain: String): Either<CoreFailure, Unit>
    suspend fun getTeam(teamId: TeamId): Flow<Team?>
    suspend fun deleteConversation(conversationId: ConversationId, teamId: String): Either<CoreFailure, Unit>
    suspend fun updateMemberRole(teamId: String, userId: String, permissionCode: Int?): Either<CoreFailure, Unit>
    suspend fun fetchTeamMember(teamId: String, userId: String): Either<CoreFailure, Unit>
    suspend fun removeTeamMember(teamId: String, userId: String): Either<CoreFailure, Unit>
    suspend fun updateTeam(team: Team): Either<CoreFailure, Unit>

}

@Suppress("LongParameterList")
internal class TeamDataSource(
    private val userDAO: UserDAO,
    private val teamDAO: TeamDAO,
    private val teamsApi: TeamsApi,
    private val userDetailsApi: UserDetailsApi,
    private val selfUserId: UserId,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val teamMapper: TeamMapper = MapperProvider.teamMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : TeamRepository {

    override suspend fun fetchTeamById(teamId: TeamId): Either<CoreFailure, Team> = wrapApiRequest {
        teamsApi.getTeamInfo(teamId = teamId.value)
    }.map { teamDTO ->
        teamMapper.fromDtoToEntity(teamDTO)
    }.map { teamEntity ->
        teamDAO.insertTeam(team = teamEntity)

        teamMapper.fromDaoModelToTeam(teamEntity)
    }

    override suspend fun fetchMembersByTeamId(teamId: TeamId, userDomain: String): Either<CoreFailure, Unit> = wrapApiRequest {
        teamsApi.getTeamMembers(
            teamId = teamId.value,
            limitTo = null
        )
    }.map { teamMemberList ->
        /**
         * If hasMore is true, then this result should be discarded and not stored locally,
         * otherwise the user will see random team members when opening the search UI.
         * If the result has has_more field set to false, then these users are stored locally to be used in a search later.
         */
        if (teamMemberList.hasMore.not()) {
            teamMemberList.members.map { teamMember ->
                userMapper.fromTeamMemberToDaoModel(
                    teamId = teamId,
                    nonQualifiedUserId = teamMember.nonQualifiedUserId,
                    permissionCode = teamMember.permissions?.own,
                    userDomain = userDomain,
                )
            }
        } else {
            listOf()
        }
    }.flatMap { teamMembers ->
        wrapStorageRequest {
            userDAO.upsertTeamMembersTypes(teamMembers)
        }
    }

    override suspend fun getTeam(teamId: TeamId): Flow<Team?> =
        teamDAO.getTeamById(teamId.value)
            .map {
                it?.let {
                    teamMapper.fromDaoModelToTeam(it)
                }
            }

    override suspend fun deleteConversation(conversationId: ConversationId, teamId: String): Either<CoreFailure, Unit> {
        return wrapApiRequest {
            teamsApi.deleteConversation(conversationId.value, teamId)
        }
    }

    override suspend fun updateMemberRole(teamId: String, userId: String, permissionCode: Int?): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            val user = userMapper.fromTeamMemberToDaoModel(
                teamId = TeamId(teamId),
                nonQualifiedUserId = userId,
                userDomain = selfUserId.domain,
                permissionCode = permissionCode
            )
            userDAO.upsertTeamMembersTypes(listOf(user))
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
                .flatMap { userProfile ->
                    wrapStorageRequest {
                        val user = userMapper.apiToEntity(
                            user = userProfile,
                            member = member,
                            teamId = teamId,
                            selfUser = idMapper.toApiModel(selfUserId)
                        )
                        userDAO.insertUser(user)
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
}
