package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.model.getPreviewAssetOrNull
import com.wire.kalium.network.api.user.details.UserProfileDTO
import com.wire.kalium.persistence.dao.UserEntity

interface PublicUserMapper {
    fun fromDaoModelToPublicUser(userEntity: UserEntity): OtherUser
    fun fromUserDetailResponse(userDetailResponse: UserProfileDTO): OtherUser
    fun fromUserDetailResponses(userDetailResponse: List<UserProfileDTO>): List<OtherUser>
    fun fromDaoConnectionStateToUser(connectionState: UserEntity.ConnectionState): ConnectionState
}

class PublicUserMapperImpl(private val idMapper: IdMapper) : PublicUserMapper {

    override fun fromDaoModelToPublicUser(userEntity: UserEntity) = OtherUser(
        id = idMapper.fromDaoModel(userEntity.id),
        name = userEntity.name,
        handle = userEntity.handle,
        email = userEntity.email,
        phone = userEntity.phone,
        accentId = userEntity.accentId,
        team = userEntity.team,
        connectionStatus = fromDaoConnectionStateToUser(connectionState = userEntity.connectionStatus),
        previewPicture = userEntity.previewAssetId,
        completePicture = userEntity.completeAssetId
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
    )

    override fun fromUserDetailResponses(userDetailResponse: List<UserProfileDTO>) =
        userDetailResponse.map { fromUserDetailResponse(it) }

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
}
