package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.TeamId
import com.wire.kalium.network.api.model.AssetSizeDTO
import com.wire.kalium.network.api.model.UserAssetDTO
import com.wire.kalium.network.api.model.UserAssetTypeDTO
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

interface UserMapper {
    fun fromDtoToSelfUser(userDTO: UserDTO): SelfUser
    fun fromApiModelToDaoModel(userProfileDTO: UserProfileDTO): UserEntity
    fun fromApiModelToDaoModel(userDTO: UserDTO): UserEntity
    fun fromDaoModelToSelfUser(userEntity: UserEntity): SelfUser

    /**
     * Maps the user data to be updated. if the parameters [newName] [newAccent] [newAssetId] are nulls,
     * it indicates that not updation should be made.
     *
     *  TODO(assets): handle deletion of assets references, emptyAssetList
     */
    fun fromModelToUpdateApiModel(user: SelfUser, newName: String?, newAccent: Int?, newAssetId: String?): UserUpdateRequest
    fun fromUpdateRequestToDaoModel(user: SelfUser, updateRequest: UserUpdateRequest): UserEntity
    fun toUserIdPersistence(userId: UserId): UserIdEntity
    fun fromTeamMemberToDaoModel(
        teamId: TeamId,
        teamMemberDTO: TeamsApi.TeamMemberDTO,
        userDomain: String
    ): UserEntity
}

internal class UserMapperImpl(
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper()
) : UserMapper {

    override fun fromDtoToSelfUser(userDTO: UserDTO): SelfUser = with(userDTO) {
        SelfUser(
            id = idMapper.fromApiModel(id),
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            team = teamId,
            connectionStatus = ConnectionState.NOT_CONNECTED,
            previewPicture = assets.getPreviewAssetOrNull()
                ?.let { idMapper.toQualifiedAssetId(it.key, id.domain) }, // assume the same domain as the userId
            completePicture = assets.getCompleteAssetOrNull()
                ?.let { idMapper.toQualifiedAssetId(it.key, id.domain) }, // assume the same domain as the userId
            availabilityStatus = UserAvailabilityStatus.NONE
        )
    }

    override fun fromApiModelToDaoModel(userProfileDTO: UserProfileDTO): UserEntity {
        return UserEntity(
            id = idMapper.fromApiToDao(userProfileDTO.id),
            name = userProfileDTO.name,
            handle = userProfileDTO.handle,
            email = null,
            phone = null,
            accentId = userProfileDTO.accentId,
            team = userProfileDTO.teamId,
            previewAssetId = userProfileDTO.assets.getPreviewAssetOrNull()?.let {
                idMapper.toQualifiedUserAssetIdEntity(it.key, userProfileDTO.id.domain)
            },
            completeAssetId = userProfileDTO.assets.getCompleteAssetOrNull()?.let {
                idMapper.toQualifiedUserAssetIdEntity(it.key, userProfileDTO.id.domain)
            },
            availabilityStatus = UserAvailabilityStatusEntity.NONE
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
        connectionStateMapper.fromDaoConnectionStateToUser(connectionState = userEntity.connectionStatus),
        userEntity.previewAssetId?.let { idMapper.fromDaoModel(it) },
        userEntity.completeAssetId?.let { idMapper.fromDaoModel(it) },
        availabilityStatusMapper.fromDaoAvailabilityStatusToModel(userEntity.availabilityStatus)
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
            connectionStatus = connectionStateMapper.fromUserConnectionStateToDao(connectionState = user.connectionStatus),
            previewAssetId = updateRequest.assets.getPreviewAssetOrNull()
                ?.let { idMapper.toQualifiedUserAssetIdEntity(it.key, user.id.domain) },
            completeAssetId = updateRequest.assets.getCompleteAssetOrNull()
                ?.let { idMapper.toQualifiedUserAssetIdEntity(it.key, user.id.domain) },
            availabilityStatus = UserAvailabilityStatusEntity.NONE
        )
    }

    override fun fromApiModelToDaoModel(userDTO: UserDTO): UserEntity = with(userDTO) {
        return UserEntity(
            id = idMapper.fromApiToDao(id),
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            team = teamId,
            previewAssetId = assets.getPreviewAssetOrNull()?.let { idMapper.toQualifiedUserAssetIdEntity(it.key, id.domain) },
            completeAssetId = assets.getCompleteAssetOrNull()?.let { idMapper.toQualifiedUserAssetIdEntity(it.key, id.domain) },
            availabilityStatus = UserAvailabilityStatusEntity.NONE
        )
    }

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
