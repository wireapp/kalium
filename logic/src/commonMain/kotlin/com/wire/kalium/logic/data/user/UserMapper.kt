package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.OtherUserClient
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.self.UserUpdateRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.UserProfileDTO
import com.wire.kalium.network.api.base.model.AssetSizeDTO
import com.wire.kalium.network.api.base.model.UserAssetDTO
import com.wire.kalium.network.api.base.model.UserAssetTypeDTO
import com.wire.kalium.network.api.base.model.UserDTO
import com.wire.kalium.network.api.base.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.base.model.getPreviewAssetOrNull
import com.wire.kalium.persistence.dao.BotEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.client.Client
import com.wire.kalium.persistence.dao.UserIDEntity as UserIdEntity

interface UserMapper {
    fun fromDtoToSelfUser(userDTO: UserDTO): SelfUser
    fun fromApiModelWithUserTypeEntityToDaoModel(
        userProfileDTO: UserProfileDTO,
        userTypeEntity: UserTypeEntity?
    ): UserEntity

    fun fromApiSelfModelToDaoModel(userDTO: UserDTO): UserEntity
    fun fromDaoModelToSelfUser(userEntity: UserEntity): SelfUser

    /**
     * Maps the user data to be updated. if the parameters [newName] [newAccent] [newAssetId] are nulls,
     * it indicates that not updation should be made.
     *
     *  TODO(assets): handle deletion of assets references, emptyAssetList
     */
    fun fromModelToUpdateApiModel(
        user: SelfUser,
        newName: String?,
        newAccent: Int?,
        newAssetId: String?
    ): UserUpdateRequest

    fun fromUpdateRequestToDaoModel(
        user: SelfUser,
        updateRequest: UserUpdateRequest
    ): UserEntity

    fun toUserIdPersistence(userId: UserId): UserIdEntity
    fun fromTeamMemberToDaoModel(
        teamId: TeamId,
        teamMemberDTO: TeamsApi.TeamMemberDTO,
        userDomain: String,
        permissionsCode: Int?,
    ): UserEntity

    fun fromOtherUsersClientsDTO(otherUsersClients: List<Client>): List<OtherUserClient>
}

internal class UserMapperImpl(
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val clientMapper: ClientMapper = MapperProvider.clientMapper(),
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper(),
    private val userEntityTypeMapper: UserEntityTypeMapper = MapperProvider.userTypeEntityMapper()
) : UserMapper {

    override fun fromDtoToSelfUser(userDTO: UserDTO): SelfUser = with(userDTO) {
        SelfUser(
            id = idMapper.fromApiModel(id),
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            teamId = teamId?.let { TeamId(it) },
            connectionStatus = ConnectionState.NOT_CONNECTED,
            previewPicture = assets.getPreviewAssetOrNull()
                ?.let { idMapper.toQualifiedAssetId(it.key, id.domain) }, // assume the same domain as the userId
            completePicture = assets.getCompleteAssetOrNull()
                ?.let { idMapper.toQualifiedAssetId(it.key, id.domain) }, // assume the same domain as the userId
            availabilityStatus = UserAvailabilityStatus.NONE
        )
    }

    override fun fromApiModelWithUserTypeEntityToDaoModel(
        userProfileDTO: UserProfileDTO,
        userTypeEntity: UserTypeEntity?
    ): UserEntity {
        return UserEntity(
            id = idMapper.fromApiToDao(userProfileDTO.id),
            name = userProfileDTO.name,
            handle = userProfileDTO.handle,
            email = userProfileDTO.email,
            phone = null, // TODO phone number not available in `UserProfileDTO`
            accentId = userProfileDTO.accentId,
            team = userProfileDTO.teamId,
            previewAssetId = userProfileDTO.assets.getPreviewAssetOrNull()?.let {
                idMapper.toQualifiedAssetIdEntity(it.key, userProfileDTO.id.domain)
            },
            completeAssetId = userProfileDTO.assets.getCompleteAssetOrNull()?.let {
                idMapper.toQualifiedAssetIdEntity(it.key, userProfileDTO.id.domain)
            },
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = userTypeEntity ?: UserTypeEntity.STANDARD,
            botService = userProfileDTO.service?.let { BotEntity(it.id, it.provider) },
            deleted = userProfileDTO.deleted ?: false
        )
    }

    override fun fromDaoModelToSelfUser(userEntity: UserEntity) = with(userEntity) {
        SelfUser(
            idMapper.fromDaoModel(id),
            name,
            handle,
            email,
            phone,
            accentId,
            team?.let { TeamId(it) },
            connectionStateMapper.fromDaoConnectionStateToUser(connectionState = connectionStatus),
            previewAssetId?.let { idMapper.fromDaoModel(it) },
            completeAssetId?.let { idMapper.fromDaoModel(it) },
            availabilityStatusMapper.fromDaoAvailabilityStatusToModel(availabilityStatus)
        )
    }

    override fun fromModelToUpdateApiModel(
        user: SelfUser,
        newName: String?,
        newAccent: Int?,
        newAssetId: String?
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

    override fun fromUpdateRequestToDaoModel(
        user: SelfUser,
        updateRequest: UserUpdateRequest
    ): UserEntity {
        return UserEntity(
            id = idMapper.toDaoModel(user.id),
            name = updateRequest.name ?: user.name,
            handle = user.handle,
            email = user.email,
            phone = user.phone,
            accentId = updateRequest.accentId ?: user.accentId,
            team = user.teamId?.value,
            connectionStatus = connectionStateMapper.fromUserConnectionStateToDao(connectionState = user.connectionStatus),
            previewAssetId = updateRequest.assets.getPreviewAssetOrNull()
                ?.let { idMapper.toQualifiedAssetIdEntity(it.key, user.id.domain) },
            completeAssetId = updateRequest.assets.getCompleteAssetOrNull()
                ?.let { idMapper.toQualifiedAssetIdEntity(it.key, user.id.domain) },
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = UserTypeEntity.STANDARD,
            botService = null,
            deleted = false
        )
    }

    override fun fromApiSelfModelToDaoModel(userDTO: UserDTO): UserEntity = with(userDTO) {
        return UserEntity(
            id = idMapper.fromApiToDao(id),
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            team = teamId,
            previewAssetId = assets.getPreviewAssetOrNull()?.let { idMapper.toQualifiedAssetIdEntity(it.key, id.domain) },
            completeAssetId = assets.getCompleteAssetOrNull()?.let { idMapper.toQualifiedAssetIdEntity(it.key, id.domain) },
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = UserTypeEntity.STANDARD,
            botService = null,
            deleted = userDTO.deleted ?: false
        )
    }

    override fun toUserIdPersistence(userId: UserId) = UserIdEntity(userId.value, userId.domain)

    /**
     * Null and default/hardcoded values will be replaced later when fetching known users.
     */
    override fun fromTeamMemberToDaoModel(
        teamId: TeamId,
        teamMemberDTO: TeamsApi.TeamMemberDTO,
        userDomain: String,
        permissionsCode: Int?,
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
            team = teamId.value,
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            previewAssetId = null,
            completeAssetId = null,
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = userEntityTypeMapper.teamRoleCodeToUserType(permissionsCode),
            botService = null,
            deleted = false
        )

    override fun fromOtherUsersClientsDTO(otherUsersClients: List<Client>): List<OtherUserClient> =
        otherUsersClients.map {
            OtherUserClient(clientMapper.fromDeviceTypeEntity(it.deviceType), it.id)
        }
}
