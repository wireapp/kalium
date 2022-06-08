package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.model.getPreviewAssetOrNull
import com.wire.kalium.network.api.user.details.UserProfileDTO
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity

interface PublicUserMapper {
    fun fromDaoModelToPublicUser(userEntity: UserEntity): OtherUser
    fun fromUserDetailResponse(userDetailResponse: UserProfileDTO): OtherUser
    fun fromUserApiToEntity(userDetailResponse: UserProfileDTO, connectionState: ConnectionEntity.State): UserEntity
    fun fromUserDetailResponses(userDetailResponse: List<UserProfileDTO>): List<OtherUser>
    fun fromPublicUserToLocalNotificationMessageAuthor(author: OtherUser?): LocalNotificationMessageAuthor
}

class PublicUserMapperImpl(
    private val idMapper: IdMapper,
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper()
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
        availabilityStatus = availabilityStatusMapper.fromDaoAvailabilityStatusToUser(userEntity.availabilityStatus)
    )

    override fun fromUserDetailResponse(userDetailResponse: UserProfileDTO) = OtherUser(
        id = UserId(userDetailResponse.id.value, userDetailResponse.id.domain),
        name = userDetailResponse.name,
        handle = userDetailResponse.handle,
        accentId = userDetailResponse.accentId,
        team = userDetailResponse.teamId,
        connectionStatus = ConnectionState.NOT_CONNECTED,
        previewPicture = userDetailResponse.assets.getPreviewAssetOrNull()?.key,
        completePicture = userDetailResponse.assets.getCompleteAssetOrNull()?.key,
        availabilityStatus = UserAvailabilityStatus.NONE
    )

    override fun fromUserApiToEntity(userDetailResponse: UserProfileDTO, connectionState: ConnectionEntity.State) = UserEntity(
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
        availabilityStatus = UserAvailabilityStatusEntity.NONE
    )

    override fun fromUserDetailResponses(userDetailResponse: List<UserProfileDTO>) =
        userDetailResponse.map { fromUserDetailResponse(it) }

    override fun fromPublicUserToLocalNotificationMessageAuthor(author: OtherUser?) =
        LocalNotificationMessageAuthor(author?.name ?: "", null)

}
