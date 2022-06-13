package com.wire.kalium.logic.data.user.mapper

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.self.model.SelfUser
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.TeamId
import com.wire.kalium.network.api.model.UserDTO
import com.wire.kalium.network.api.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.model.getPreviewAssetOrNull
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.network.api.user.details.UserProfileDTO
import com.wire.kalium.network.api.user.self.UserUpdateRequest
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserIDEntity as UserIdEntity

interface UserEntityMapper {
    fun fromUserProfileDTO(userProfileDTO: UserProfileDTO): UserEntity
    fun fromUserDTO(userDTO: UserDTO): UserEntity
    fun fromUserProfileDTOWithConnectionState(userProfileDTO: UserProfileDTO, connectionState: ConnectionEntity.State): UserEntity
    fun fromUpdateRequestToDaoModel(selfUser: SelfUser, updateRequest: UserUpdateRequest): UserEntity
    fun toUserIdPersistence(userId: UserId): UserIdEntity
    fun fromTeamMemberToDaoModel(
        teamId: TeamId,
        teamMemberDTO: TeamsApi.TeamMemberDTO,
        userDomain: String
    ): UserEntity
}

internal class UserEntityMapperImpl(
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper()
) : UserEntityMapper {

    override fun fromUserProfileDTO(userProfileDTO: UserProfileDTO): UserEntity {
        return UserEntity(
            id = idMapper.fromApiToDao(userProfileDTO.id),
            name = userProfileDTO.name,
            handle = userProfileDTO.handle,
            email = null,
            phone = null,
            accentId = userProfileDTO.accentId,
            team = userProfileDTO.teamId,
            previewAssetId = userProfileDTO.assets.getPreviewAssetOrNull()?.key,
            completeAssetId = userProfileDTO.assets.getCompleteAssetOrNull()?.key,
            availabilityStatus = UserAvailabilityStatusEntity.NONE
        )
    }

    override fun fromUpdateRequestToDaoModel(selfUser: SelfUser, updateRequest: UserUpdateRequest): UserEntity {
        return UserEntity(
            id = idMapper.toDaoModel(selfUser.id),
            name = updateRequest.name ?: selfUser.name,
            handle = selfUser.handle,
            email = selfUser.email,
            phone = selfUser.phone,
            accentId = updateRequest.accentId ?: selfUser.accentId,
            team = selfUser.team,
            connectionStatus = connectionStateMapper.fromUserConnectionStateToDao(connectionState = selfUser.connectionStatus),
            previewAssetId = updateRequest.assets?.getPreviewAssetOrNull()?.key,
            completeAssetId = updateRequest.assets?.getCompleteAssetOrNull()?.key,
            availabilityStatus = UserAvailabilityStatusEntity.NONE
        )
    }

    override fun fromUserDTO(userDTO: UserDTO): UserEntity = with(userDTO) {
        return UserEntity(
            id = idMapper.fromApiToDao(id),
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            team = teamId,
            previewAssetId = assets.getPreviewAssetOrNull()?.key,
            completeAssetId = assets.getCompleteAssetOrNull()?.key,
            availabilityStatus = UserAvailabilityStatusEntity.NONE
        )
    }

    override fun fromUserProfileDTOWithConnectionState(userProfileDTO: UserProfileDTO, connectionState: ConnectionEntity.State) = UserEntity(
        id = idMapper.fromApiToDao(userProfileDTO.id),
        name = userProfileDTO.name,
        handle = userProfileDTO.handle,
        email = userProfileDTO.email,
        phone = null,
        accentId = userProfileDTO.accentId,
        team = userProfileDTO.teamId,
        previewAssetId = userProfileDTO.assets.getPreviewAssetOrNull()?.key,
        completeAssetId = userProfileDTO.assets.getCompleteAssetOrNull()?.key,
        connectionStatus = connectionState,
        availabilityStatus = UserAvailabilityStatusEntity.NONE
    )

    override fun toUserIdPersistence(userId: UserId) = UserIdEntity(userId.value, userId.domain)

    /**
     * Null and default/hardcoded values will be replaced later when fetching known users.
     */
    override fun fromTeamMemberToDaoModel(
        teamId: TeamId,
        teamMemberDTO: TeamsApi.TeamMemberDTO,
        userDomain: String
    ): UserEntity =
        UserEntity(
            id = QualifiedIDEntity(
                value = teamMemberDTO.nonQualifiedUserId,
                domain = userDomain
            ),
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 1,
            team = teamId,
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            previewAssetId = null,
            completeAssetId = null,
            availabilityStatus = UserAvailabilityStatusEntity.NONE
        )

}
