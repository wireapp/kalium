package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.BotService
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.OtherUserMinimized
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.authenticated.userDetails.UserProfileDTO
import com.wire.kalium.network.api.base.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.base.model.getPreviewAssetOrNull
import com.wire.kalium.persistence.dao.BotEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserEntityMinimized
import com.wire.kalium.persistence.dao.UserTypeEntity

interface PublicUserMapper {
    fun fromDaoModelToPublicUser(userEntity: UserEntity): OtherUser
    fun fromDaoModelToPublicUserMinimized(userEntity: UserEntityMinimized): OtherUserMinimized
    fun fromUserDetailResponseWithUsertype(
        userDetailResponse: UserProfileDTO,
        // UserProfileDTO has no info about userType, we need to pass it explicitly
        userType: UserType
    ): OtherUser

    // TODO:I think we are making too complicated parsers,
    // maybe a good solution will be fetching self user when we are saving other users to db?
    fun fromUserApiToEntityWithConnectionStateAndUserTypeEntity(
        userDetailResponse: UserProfileDTO,
        connectionState: ConnectionEntity.State,
        // UserProfileDTO has no info about userType, we need to pass it explicitly
        userTypeEntity: UserTypeEntity
    ): UserEntity
}

class PublicUserMapperImpl(
    private val idMapper: IdMapper,
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper(),
    private val domainUserTypeMapper: DomainUserTypeMapper = MapperProvider.userTypeMapper()
) : PublicUserMapper {

    override fun fromDaoModelToPublicUser(userEntity: UserEntity) = OtherUser(
        id = idMapper.fromDaoModel(userEntity.id),
        name = userEntity.name,
        handle = userEntity.handle,
        email = userEntity.email,
        phone = userEntity.phone,
        accentId = userEntity.accentId,
        teamId = userEntity.team?.let { TeamId(it) },
        connectionStatus = connectionStateMapper.fromDaoConnectionStateToUser(connectionState = userEntity.connectionStatus),
        previewPicture = userEntity.previewAssetId?.let { idMapper.fromDaoModel(it) },
        completePicture = userEntity.completeAssetId?.let { idMapper.fromDaoModel(it) },
        availabilityStatus = availabilityStatusMapper.fromDaoAvailabilityStatusToModel(userEntity.availabilityStatus),
        userType = domainUserTypeMapper.fromUserTypeEntity(userEntity.userType),
        botService = userEntity.botService?.let { BotService(it.id, it.provider) },
        deleted = userEntity.deleted
    )

    override fun fromDaoModelToPublicUserMinimized(userEntity: UserEntityMinimized): OtherUserMinimized =
        OtherUserMinimized(
            id = idMapper.fromDaoModel(userEntity.id),
            name = userEntity.name,
            completePicture = userEntity.completeAssetId?.let { idMapper.fromDaoModel(it) },
            userType = domainUserTypeMapper.fromUserTypeEntity(userEntity.userType),
        )

    override fun fromUserDetailResponseWithUsertype(
        userDetailResponse: UserProfileDTO,
        userType: UserType
    ) = OtherUser(
        id = UserId(userDetailResponse.id.value, userDetailResponse.id.domain),
        name = userDetailResponse.name,
        handle = userDetailResponse.handle,
        accentId = userDetailResponse.accentId,
        teamId = userDetailResponse.teamId?.let { TeamId(it) },
        connectionStatus = ConnectionState.NOT_CONNECTED,
        previewPicture = userDetailResponse.assets.getPreviewAssetOrNull()
            ?.let { idMapper.toQualifiedAssetId(it.key, userDetailResponse.id.domain) },
        completePicture = userDetailResponse.assets.getCompleteAssetOrNull()
            ?.let { idMapper.toQualifiedAssetId(it.key, userDetailResponse.id.domain) },
        availabilityStatus = UserAvailabilityStatus.NONE,
        userType = userType,
        botService = userDetailResponse.service?.let { BotService(it.id, it.provider) },
        deleted = userDetailResponse.deleted ?: false
    )

    override fun fromUserApiToEntityWithConnectionStateAndUserTypeEntity(
        userDetailResponse: UserProfileDTO,
        connectionState: ConnectionEntity.State,
        userTypeEntity: UserTypeEntity
    ) = UserEntity(
        id = idMapper.fromApiToDao(userDetailResponse.id),
        name = userDetailResponse.name,
        handle = userDetailResponse.handle,
        email = userDetailResponse.email,
        phone = null,
        accentId = userDetailResponse.accentId,
        team = userDetailResponse.teamId,
        previewAssetId = userDetailResponse.assets.getPreviewAssetOrNull()
            ?.let { idMapper.toQualifiedAssetIdEntity(it.key, userDetailResponse.id.domain) },
        completeAssetId = userDetailResponse.assets.getCompleteAssetOrNull()
            ?.let { idMapper.toQualifiedAssetIdEntity(it.key, userDetailResponse.id.domain) },
        connectionStatus = connectionState,
        availabilityStatus = UserAvailabilityStatusEntity.NONE,
        userType = userTypeEntity,
        botService = userDetailResponse.service?.let { BotEntity(it.id, it.provider) },
        deleted = userDetailResponse.deleted ?: false
    )

}
