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
import com.wire.kalium.network.api.user.details.UserProfileDTO
import com.wire.kalium.network.api.user.self.UserUpdateRequest
import com.wire.kalium.persistence.dao.QualifiedIDEntity
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
        teamMember: TeamsApi.TeamMember,
        userDomain: String
    ): UserEntity
    fun fromDaoConnectionStateToUser(connectionState: UserEntity.ConnectionState): ConnectionState
    fun fromUserConnectionStateToDao(connectionState: ConnectionState): UserEntity.ConnectionState
}

internal class UserMapperImpl(private val idMapper: IdMapper) : UserMapper {

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
            previewPicture = assets.getPreviewAssetOrNull()?.key,
            completePicture = assets.getCompleteAssetOrNull()?.key
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
            previewAssetId = userProfileDTO.assets.getPreviewAssetOrNull()?.key,
            completeAssetId = userProfileDTO.assets.getCompleteAssetOrNull()?.key
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
        fromDaoConnectionStateToUser(connectionState = userEntity.connectionStatus),
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
            connectionStatus = fromUserConnectionStateToDao(connectionState = user.connectionStatus),
            previewAssetId = updateRequest.assets?.getPreviewAssetOrNull()?.key,
            completeAssetId = updateRequest.assets?.getCompleteAssetOrNull()?.key
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
            previewAssetId = assets.getPreviewAssetOrNull()?.key,
            completeAssetId = assets.getCompleteAssetOrNull()?.key
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
            connectionStatus = UserEntity.ConnectionState.ACCEPTED,
            previewAssetId = null,
            completeAssetId = null
        )

    override fun fromDaoConnectionStateToUser(connectionState: UserEntity.ConnectionState): ConnectionState =
        when(connectionState) {
            UserEntity.ConnectionState.NOT_CONNECTED -> ConnectionState.NOT_CONNECTED
            UserEntity.ConnectionState.PENDING -> ConnectionState.PENDING
            UserEntity.ConnectionState.SENT -> ConnectionState.SENT
            UserEntity.ConnectionState.BLOCKED -> ConnectionState.BLOCKED
            UserEntity.ConnectionState.IGNORED -> ConnectionState.IGNORED
            UserEntity.ConnectionState.CANCELLED -> ConnectionState.CANCELLED
            UserEntity.ConnectionState.MISSING_LEGALHOLD_CONSENT -> ConnectionState.MISSING_LEGALHOLD_CONSENT
            UserEntity.ConnectionState.ACCEPTED -> ConnectionState.ACCEPTED
        }

    override fun fromUserConnectionStateToDao(connectionState: ConnectionState): UserEntity.ConnectionState =
        when(connectionState) {
            ConnectionState.NOT_CONNECTED -> UserEntity.ConnectionState.NOT_CONNECTED
            ConnectionState.PENDING -> UserEntity.ConnectionState.PENDING
            ConnectionState.SENT -> UserEntity.ConnectionState.SENT
            ConnectionState.BLOCKED -> UserEntity.ConnectionState.BLOCKED
            ConnectionState.IGNORED -> UserEntity.ConnectionState.IGNORED
            ConnectionState.CANCELLED -> UserEntity.ConnectionState.CANCELLED
            ConnectionState.MISSING_LEGALHOLD_CONSENT -> UserEntity.ConnectionState.MISSING_LEGALHOLD_CONSENT
            ConnectionState.ACCEPTED -> UserEntity.ConnectionState.ACCEPTED
        }
}
