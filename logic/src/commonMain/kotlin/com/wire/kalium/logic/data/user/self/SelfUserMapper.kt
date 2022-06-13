package com.wire.kalium.logic.data.user.self

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.ConnectionStateMapper
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.model.UserDTO
import com.wire.kalium.network.api.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.model.getPreviewAssetOrNull
import com.wire.kalium.persistence.dao.UserEntity

interface SelfUserMapper {
    fun fromDtoToSelfUser(userDTO: UserDTO): SelfUser
    fun fromDaoModelToSelfUser(userEntity: UserEntity): SelfUser
}

class SelfUserMapperImpl(
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val connectionStateMapper: ConnectionStateMapper = MapperProvider.connectionStateMapper(),
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
) : SelfUserMapper {

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
            completePicture = assets.getCompleteAssetOrNull()?.key,
            availabilityStatus = UserAvailabilityStatus.NONE
        )
    }

    override fun fromDaoModelToSelfUser(userEntity: UserEntity) = SelfUser(
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

}
