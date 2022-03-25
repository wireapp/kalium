package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.TeamId
import com.wire.kalium.network.api.model.AssetSizeDTO
import com.wire.kalium.network.api.model.UserAssetDTO
import com.wire.kalium.network.api.model.UserAssetTypeDTO
import com.wire.kalium.network.api.model.UserDTO
import com.wire.kalium.network.api.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.model.getPreviewAssetOrNull
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.network.api.user.details.UserDetailsResponse
import com.wire.kalium.network.api.user.self.UserUpdateRequest
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserIDEntity as UserIdEntity

interface UserMapper {
    fun fromDtoToSelfUser(userDTO: UserDTO): SelfUser
    fun fromApiModelToDaoModel(userDetailsResponse: UserDetailsResponse): UserEntity
    fun fromApiModelToDaoModel(userDTO: UserDTO): UserEntity
    fun fromDaoModelToSelfUser(userEntity: UserEntity): SelfUser
    /**
     * Maps the user data to be updated. if the parameters [newName] [newAccent] [newAssetId] are nulls,
     * it indicates that not updation should be made.
     *
     *  TODO: handle deletion of assets references, emptyAssetList
     */
    fun fromModelToUpdateApiModel(user: SelfUser, newName: String?, newAccent: Int?, newAssetId: String?): UserUpdateRequest
    fun fromUpdateRequestToDaoModel(user: SelfUser, updateRequest: UserUpdateRequest): UserEntity
    fun toUserIdPersistence(userId: UserId): UserIdEntity
    fun fromTeamMemberToDaoModel(
        teamId: TeamId,
        teamMember: TeamsApi.TeamMember,
        userDomain: String
    ): UserEntity
}

internal class UserMapperImpl(private val idMapper: IdMapper) : UserMapper {

    override fun fromDtoToSelfUser(userDTO: UserDTO): SelfUser = with(userDTO) {
        SelfUser(
            idMapper.fromApiModel(id),
            name,
            handle,
            email,
            phone,
            accentId,
            teamId,
            assets.getPreviewAssetOrNull()?.key,
            assets.getCompleteAssetOrNull()?.key
        )
    }

    override fun fromApiModelToDaoModel(userDetailsResponse: UserDetailsResponse): UserEntity {
        return UserEntity(
            id = idMapper.fromApiToDao(userDetailsResponse.id),
            name = userDetailsResponse.name,
            handle = userDetailsResponse.handle,
            email = null,
            phone = null,
            accentId = userDetailsResponse.accentId,
            team = userDetailsResponse.team,
            previewAssetId = userDetailsResponse.assets.getPreviewAssetOrNull()?.key,
            completeAssetId = userDetailsResponse.assets.getCompleteAssetOrNull()?.key
        )
    }

    override fun fromDaoModelToSelfUser(userEntity: UserEntity) = SelfUser(
        idMapper.fromDaoModel(userEntity.id),
        userEntity.name,
        userEntity.handle,
        userEntity.email,
        userEntity.phone,
        userEntity.accentId,
        userEntity.team,
        userEntity.previewAssetId,
        userEntity.completeAssetId
    )

    override fun fromModelToUpdateApiModel(
        user: SelfUser, newName: String?, newAccent: Int?, newAssetId: String?
    ): UserUpdateRequest {
        return UserUpdateRequest(
            name = newName, accentId = newAccent, assets = if (newAssetId != null) {
                listOf(
                    UserAssetDTO(newAssetId, AssetSizeDTO.COMPLETE, UserAssetTypeDTO.IMAGE),
                    UserAssetDTO(newAssetId, AssetSizeDTO.PREVIEW, UserAssetTypeDTO.IMAGE)
                )
            } else {
                null
            }
        )
    }

    override fun fromUpdateRequestToDaoModel(user: SelfUser, updateRequest: UserUpdateRequest): UserEntity {
        return UserEntity(
            id = idMapper.toDaoModel(user.id),
            name = updateRequest.name ?: user.name,
            handle = user.handle,
            email = user.email,
            phone = user.phone,
            accentId = updateRequest.accentId ?: user.accentId,
            team = user.team,
            previewAssetId = updateRequest.assets?.getPreviewAssetOrNull()?.key,
            completeAssetId = updateRequest.assets?.getCompleteAssetOrNull()?.key
        )
    }

    override fun fromApiModelToDaoModel(userDTO: UserDTO): UserEntity = with(userDTO) {
        return UserEntity(
            idMapper.fromApiToDao(id),
            name,
            handle,
            email,
            phone,
            accentId,
            teamId,
            assets.getPreviewAssetOrNull()?.key,
            assets.getCompleteAssetOrNull()?.key
        )
    }

    override fun toUserIdPersistence(userId: UserId) = UserIdEntity(userId.value, userId.domain)

    /**
     * Null and default/hardcoded values will be replaced later when fetching known users.
     */
    override fun fromTeamMemberToDaoModel(
        teamId: TeamId,
        teamMember: TeamsApi.TeamMember,
        userDomain: String
    ): UserEntity =
        UserEntity(
            id = QualifiedIDEntity(
                value = teamMember.nonQualifiedUserId,
                domain = userDomain
            ),
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 1,
            team = teamId,
            previewAssetId = null,
            completeAssetId = null
        )
}
