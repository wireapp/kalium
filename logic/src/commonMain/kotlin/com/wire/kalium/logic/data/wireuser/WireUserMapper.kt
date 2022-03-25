package com.wire.kalium.logic.data.wireuser

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.wireuser.model.PublicUser
import com.wire.kalium.network.api.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.model.getPreviewAssetOrNull
import com.wire.kalium.network.api.user.details.UserDetailsResponse
import com.wire.kalium.persistence.dao.UserEntity

interface WireUserMapper {
    fun fromDaoModelToWireUser(userEntity: UserEntity): PublicUser
    fun fromUserDetailResponse(userDetailResponse: UserDetailsResponse): PublicUser
    fun fromUserDetailResponses(userDetailResponse: List<UserDetailsResponse>): List<PublicUser>
}

class WireUserMapperImpl(private val idMapper: IdMapper) : WireUserMapper {

    override fun fromDaoModelToWireUser(userEntity: UserEntity) = PublicUser(
        idMapper.fromDaoModel(userEntity.id),
        userEntity.name,
        userEntity.handle,
        userEntity.email,
        userEntity.phone,
        userEntity.accentId,
        userEntity.team,
        userEntity.previewAssetId,
        userEntity.completeAssetId
    )

    override fun fromUserDetailResponse(userDetailResponse: UserDetailsResponse) = PublicUser(
        id = UserId(userDetailResponse.id.value, userDetailResponse.id.domain),
        name = userDetailResponse.name,
        handle = userDetailResponse.handle,
        accentId = userDetailResponse.accentId,
        team = userDetailResponse.team,
        previewPicture = userDetailResponse.assets.getPreviewAssetOrNull()?.key,
        completePicture = userDetailResponse.assets.getCompleteAssetOrNull()?.key,
    )

    override fun fromUserDetailResponses(userDetailResponse: List<UserDetailsResponse>) =
        userDetailResponse.map { fromUserDetailResponse(it) }

}
