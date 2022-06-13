package com.wire.kalium.logic.data.user.mapper

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.ConnectionStateMapper
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

interface UserEntityMapper {
    fun fromUserProfileDTO(userProfileDTO: UserProfileDTO): UserEntity
    fun fromUserDTO(userDTO: UserDTO): UserEntity
    fun fromUserProfileDTOWithConnectionState(userProfileDTO: UserProfileDTO, connectionState: ConnectionEntity.State): UserEntity
    fun fromSelfUserWithUserUpdateRequest(selfUser: SelfUser, userUpdateRequest: UserUpdateRequest): UserEntity
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
        with(userProfileDTO) {
            return UserEntity(
                id = idMapper.fromApiToDao(id),
                name = name,
                handle = handle,
                email = null,
                phone = null,
                accentId = accentId,
                team = teamId,
                previewAssetId = assets.getPreviewAssetOrNull()?.key,
                completeAssetId = assets.getCompleteAssetOrNull()?.key,
                availabilityStatus = UserAvailabilityStatusEntity.NONE
            )
        }
    }

    override fun fromSelfUserWithUserUpdateRequest(selfUser: SelfUser, userUpdateRequest: UserUpdateRequest): UserEntity {
        with(selfUser) {
            return UserEntity(
                id = idMapper.toDaoModel(id),
                name = userUpdateRequest.name ?: name,
                handle = handle,
                email = email,
                phone = phone,
                accentId = userUpdateRequest.accentId ?: accentId,
                team = team,
                connectionStatus = connectionStateMapper.fromUserConnectionStateToDao(connectionState = connectionStatus),
                previewAssetId = userUpdateRequest.assets?.getPreviewAssetOrNull()?.key,
                completeAssetId = userUpdateRequest.assets?.getCompleteAssetOrNull()?.key,
                availabilityStatus = UserAvailabilityStatusEntity.NONE
            )
        }
    }

    override fun fromUserDTO(userDTO: UserDTO): UserEntity = with(userDTO) {
        UserEntity(
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

    override fun fromUserProfileDTOWithConnectionState(userProfileDTO: UserProfileDTO, connectionState: ConnectionEntity.State) =
        with(userProfileDTO) {
            UserEntity(
                id = idMapper.fromApiToDao(id),
                name = name,
                handle = handle,
                email = email,
                phone = null,
                accentId = accentId,
                team = teamId,
                previewAssetId = assets.getPreviewAssetOrNull()?.key,
                completeAssetId = assets.getCompleteAssetOrNull()?.key,
                connectionStatus = connectionState,
                availabilityStatus = UserAvailabilityStatusEntity.NONE
            )
        }

    /**
     * Null and default/hardcoded values will be replaced later when fetching known users.
     */
    override fun fromTeamMemberToDaoModel(
        teamId: TeamId,
        teamMemberDTO: TeamsApi.TeamMemberDTO,
        userDomain: String
    ): UserEntity = UserEntity(
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
