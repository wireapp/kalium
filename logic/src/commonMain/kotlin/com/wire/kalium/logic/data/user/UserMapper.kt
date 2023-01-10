package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.OtherUserClient
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.type.UserEntityTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.self.UserUpdateRequest
import com.wire.kalium.network.api.base.authenticated.userDetails.UserProfileDTO
import com.wire.kalium.network.api.base.model.AssetSizeDTO
import com.wire.kalium.network.api.base.model.NonQualifiedUserId
import com.wire.kalium.network.api.base.model.QualifiedID
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
    fun fromSelfUserToDaoModel(selfUser: SelfUser): UserEntity

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
        nonQualifiedUserId: NonQualifiedUserId,
        permissionCode: Int?,
        userDomain: String,
    ): UserEntity

    fun fromOtherUsersClientsDTO(otherUsersClients: List<Client>): List<OtherUserClient>

    fun apiToEntity(user: UserProfileDTO, member: TeamsApi.TeamMemberDTO?, teamId: String?, selfUser: QualifiedID): UserEntity
    fun toUpdateDaoFromEvent(event: Event.User.Update, userEntity: UserEntity): UserEntity
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
            id = id.toModel(),
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            teamId = teamId?.let { TeamId(it) },
            connectionStatus = ConnectionState.NOT_CONNECTED,
            previewPicture = assets.getPreviewAssetOrNull()?.toModel(id.domain), // assume the same domain as the userId
            completePicture = assets.getCompleteAssetOrNull()?.toModel(id.domain), // assume the same domain as the userId
            availabilityStatus = UserAvailabilityStatus.NONE
        )
    }

    override fun fromApiModelWithUserTypeEntityToDaoModel(
        userProfileDTO: UserProfileDTO,
        userTypeEntity: UserTypeEntity?
    ): UserEntity {
        return UserEntity(
            id = userProfileDTO.id.toDao(),
            name = userProfileDTO.name,
            handle = userProfileDTO.handle,
            email = userProfileDTO.email,
            phone = null, // TODO phone number not available in `UserProfileDTO`
            accentId = userProfileDTO.accentId,
            team = userProfileDTO.teamId,
            previewAssetId = userProfileDTO.assets.getPreviewAssetOrNull()?.toDao(userProfileDTO.id.domain),
            completeAssetId = userProfileDTO.assets.getCompleteAssetOrNull()?.toDao(userProfileDTO.id.domain),
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = userTypeEntity ?: UserTypeEntity.STANDARD,
            botService = userProfileDTO.service?.let { BotEntity(it.id, it.provider) },
            deleted = userProfileDTO.deleted ?: false
        )
    }

    override fun fromDaoModelToSelfUser(userEntity: UserEntity) = with(userEntity) {
        SelfUser(
            id.toModel(),
            name,
            handle,
            email,
            phone,
            accentId,
            team?.let { TeamId(it) },
            connectionStateMapper.fromDaoConnectionStateToUser(connectionState = connectionStatus),
            previewAssetId?.toModel(),
            completeAssetId?.toModel(),
            availabilityStatusMapper.fromDaoAvailabilityStatusToModel(availabilityStatus)
        )
    }

    override fun fromSelfUserToDaoModel(selfUser: SelfUser): UserEntity = with(selfUser) {
        UserEntity(
            id = id.toDao(),
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            team = teamId?.value,
            connectionStatus = connectionStateMapper.fromUserConnectionStateToDao(connectionStatus),
            previewAssetId = previewPicture?.toDao(),
            completeAssetId = completePicture?.toDao(),
            availabilityStatus = availabilityStatusMapper.fromModelAvailabilityStatusToDao(availabilityStatus),
            userType = UserTypeEntity.STANDARD,
            botService = null,
            deleted = false
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
            id = user.id.toDao(),
            name = updateRequest.name ?: user.name,
            handle = user.handle,
            email = user.email,
            phone = user.phone,
            accentId = updateRequest.accentId ?: user.accentId,
            team = user.teamId?.value,
            connectionStatus = connectionStateMapper.fromUserConnectionStateToDao(connectionState = user.connectionStatus),
            previewAssetId = updateRequest.assets.getPreviewAssetOrNull()?.toDao(user.id.domain),
            completeAssetId = updateRequest.assets.getCompleteAssetOrNull()?.toDao(user.id.domain),
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
            previewAssetId = assets.getPreviewAssetOrNull()?.let { QualifiedIDEntity(it.key, id.domain) },
            completeAssetId = assets.getCompleteAssetOrNull()?.let { QualifiedIDEntity(it.key, id.domain) },
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
        nonQualifiedUserId: NonQualifiedUserId,
        permissionCode: Int?,
        userDomain: String,
    ): UserEntity =
        UserEntity(
            id = QualifiedIDEntity(
                value = nonQualifiedUserId,
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
            userType = userEntityTypeMapper.teamRoleCodeToUserType(permissionCode),
            botService = null,
            deleted = false
        )

    override fun fromOtherUsersClientsDTO(otherUsersClients: List<Client>): List<OtherUserClient> =
        otherUsersClients.map {
            OtherUserClient(clientMapper.fromDeviceTypeEntity(it.deviceType), it.id, it.isValid)
        }

    override fun apiToEntity(user: UserProfileDTO, member: TeamsApi.TeamMemberDTO?, teamId: String?, selfUser: QualifiedID): UserEntity {
        return UserEntity(
            id = idMapper.fromApiToDao(user.id),
            name = user.name,
            handle = user.handle,
            email = user.email,
            phone = null,
            accentId = user.accentId,
            team = teamId ?: user.teamId,
            connectionStatus = member?.let { ConnectionEntity.State.ACCEPTED } ?: ConnectionEntity.State.NOT_CONNECTED,
            previewAssetId = user.assets.getPreviewAssetOrNull()?.toDao(user.id.domain),
            completeAssetId = user.assets.getCompleteAssetOrNull()?.toDao(user.id.domain),
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userType = member?.permissions?.let { userEntityTypeMapper.teamRoleCodeToUserType(it.own) }
                ?: userEntityTypeMapper.fromTeamAndDomain(
                    otherUserDomain = user.id.domain,
                    selfUserDomain = selfUser.domain,
                    selfUserTeamId = teamId,
                    otherUserTeamId = teamId,
                    isService = user.service != null
                ),
            botService = user.service?.let { BotEntity(it.id, it.provider) },
            deleted = false
        )
    }

    override fun toUpdateDaoFromEvent(event: Event.User.Update, userEntity: UserEntity): UserEntity {
        return userEntity.let { persistedEntity ->
            persistedEntity.copy(
                email = event.email ?: persistedEntity.email,
                name = event.name ?: persistedEntity.name,
                handle = event.handle ?: persistedEntity.handle,
                accentId = event.accentId ?: persistedEntity.accentId,
                previewAssetId = event.previewAssetId?.let { QualifiedIDEntity(it, persistedEntity.id.domain) }
                    ?: persistedEntity.previewAssetId,
                completeAssetId = event.completeAssetId?.let { QualifiedIDEntity(it, persistedEntity.id.domain) }
                    ?: persistedEntity.completeAssetId
            )
        }
    }
}
