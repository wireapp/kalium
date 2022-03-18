package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.data.publicuser.model.PublicUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.model.getCompleteAssetOrNull
import com.wire.kalium.network.api.model.getPreviewAssetOrNull
import com.wire.kalium.network.api.user.details.UserDetailsResponse

interface PublicUserMapper {
    fun fromUserDetailResponse(userDetailResponse: UserDetailsResponse): PublicUser
    fun fromUserDetailResponses(userDetailResponse: List<UserDetailsResponse>): List<PublicUser>
}

class PublicUserMapperImpl : PublicUserMapper {

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
        userDetailResponse.map {
            fromUserDetailResponse(it)
        }

}
