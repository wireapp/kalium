package com.wire.kalium.logic.data.user.other.mapper

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.other.model.OtherUser
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.model.getPreviewAssetOrNull
import com.wire.kalium.network.api.user.details.UserProfileDTO
import com.wire.kalium.persistence.dao.UserEntity

interface OtherUserMapper {
    fun fromUserEntity(userEntity: UserEntity): OtherUser
    fun fromUserProfileDTO(userProfileDTO: UserProfileDTO): OtherUser
    fun fromUserProfileDTOs(userProfileDTOs: List<UserProfileDTO>): List<OtherUser>
}

class OtherUserMapperImpl(
    private val idMapper: IdMapper,
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper()
) : OtherUserMapper {

    override fun fromUserEntity(userEntity: UserEntity) = OtherUser(
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
        availabilityStatus = availabilityStatusMapper.fromDaoAvailabilityStatusToModel(userEntity.availabilityStatus)
    )

    override fun fromUserProfileDTO(userProfileDTO: UserProfileDTO) = OtherUser(
        id = UserId(userProfileDTO.id.value, userProfileDTO.id.domain),
        name = userProfileDTO.name,
        handle = userProfileDTO.handle,
        accentId = userProfileDTO.accentId,
        team = userProfileDTO.teamId,
        connectionStatus = ConnectionState.NOT_CONNECTED,
        previewPicture = userProfileDTO.assets.getPreviewAssetOrNull()?.key,
        completePicture = userProfileDTO.assets.getCompleteAssetOrNull()?.key,
        availabilityStatus = UserAvailabilityStatus.NONE
    )

    override fun fromUserProfileDTOs(userProfileDTOs: List<UserProfileDTO>) =
        userProfileDTOs.map { fromUserProfileDTO(it) }

}
