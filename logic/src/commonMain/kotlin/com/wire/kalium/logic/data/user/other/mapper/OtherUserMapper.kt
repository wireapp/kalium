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

    override fun fromUserEntity(userEntity: UserEntity) = with(userEntity) {
        OtherUser(
            id = idMapper.fromDaoModel(id),
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            accentId = accentId,
            team = team,
            connectionStatus = connectionStateMapper.fromDaoConnectionStateToUser(connectionState = connectionStatus),
            previewPicture = previewAssetId,
            completePicture = completeAssetId,
            availabilityStatus = availabilityStatusMapper.fromDaoAvailabilityStatusToModel(status = availabilityStatus)
        )
    }

    override fun fromUserProfileDTO(userProfileDTO: UserProfileDTO) = with(userProfileDTO) {
        OtherUser(
            id = UserId(id.value, id.domain),
            name = name,
            handle = handle,
            accentId = accentId,
            team = teamId,
            connectionStatus = ConnectionState.NOT_CONNECTED,
            previewPicture = assets.getPreviewAssetOrNull()?.key,
            completePicture = assets.getCompleteAssetOrNull()?.key,
            availabilityStatus = UserAvailabilityStatus.NONE
        )
    }

    override fun fromUserProfileDTOs(userProfileDTOs: List<UserProfileDTO>) =
        userProfileDTOs.map { fromUserProfileDTO(it) }

}
