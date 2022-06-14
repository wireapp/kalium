package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.data.conversation.UserType
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.model.getPreviewAssetOrNull
import com.wire.kalium.network.api.user.details.UserProfileDTO
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity

interface PublicUserMapper {
    fun fromDaoModelToPublicUser(userEntity: UserEntity): OtherUser
    fun fromUserDetailResponseWithUsertype(
        userDetailResponse: UserProfileDTO,
        // UserProfileDTO has no info about userType, we need to pass it explicitly
        userType: UserType
    ): OtherUser

    fun fromUserApiToEntityWithConnectionStateAndUserTypeEntity(
        userDetailResponse: UserProfileDTO,
        connectionState: ConnectionEntity.State,
        // UserProfileDTO has no info about userType, we need to pass it explicitly
        userTypeEntity: UserTypeEntity
    ): UserEntity

//    fun fromUserDetailResponses(userDetailResponse: List<UserProfileDTO>): List<OtherUser>
}

class PublicUserMapperImpl(
    private val userDAO: UserDAO,
    private val metadataDAO: MetadataDAO,
    private val idMapper: IdMapper,
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper(),
    private val domainUserTypeMapper: DomainUserTypeMapper = MapperProvider.userTypeMapper(userDAO,metadataDAO)
) : PublicUserMapper {

    override fun fromDaoModelToPublicUser(userEntity: UserEntity) = OtherUser(
        id = idMapper.fromDaoModel(userEntity.id),
        name = userEntity.name,
        handle = userEntity.handle,
        email = userEntity.email,
        phone = userEntity.phone,
        accentId = userEntity.accentId,
        team = userEntity.team,
        connectionStatus = connectionStateMapper.fromDaoConnectionStateToUser(connectionState = userEntity.connectionStatus),
        previewPicture = userEntity.previewAssetId,
        completePicture = userEntity.completeAssetId,
        availabilityStatus = availabilityStatusMapper.fromDaoAvailabilityStatusToModel(userEntity.availabilityStatus),
        userType = domainUserTypeMapper.fromUserTypeEntity(userEntity.userTypEntity)
    )

    override fun fromUserDetailResponseWithUsertype(
        userDetailResponse: UserProfileDTO,
        userType: UserType
    ) = OtherUser(
        id = UserId(userDetailResponse.id.value, userDetailResponse.id.domain),
        name = userDetailResponse.name,
        handle = userDetailResponse.handle,
        accentId = userDetailResponse.accentId,
        team = userDetailResponse.teamId,
        connectionStatus = ConnectionState.NOT_CONNECTED,
        previewPicture = userDetailResponse.assets.getPreviewAssetOrNull()?.key,
        completePicture = userDetailResponse.assets.getCompleteAssetOrNull()?.key,
        availabilityStatus = UserAvailabilityStatus.NONE,
        userType = userType
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
        previewAssetId = userDetailResponse.assets.getPreviewAssetOrNull()?.key,
        completeAssetId = userDetailResponse.assets.getCompleteAssetOrNull()?.key,
        connectionStatus = connectionState,
        availabilityStatus = UserAvailabilityStatusEntity.NONE,
        userTypEntity = userTypeEntity
    )

//    override fun fromUserDetailResponses(userDetailResponse: List<UserProfileDTO>) =
//        userDetailResponse.map { fromUserDetailResponseWithUsertype(it) }


}
