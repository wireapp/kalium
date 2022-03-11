package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.data.publicuser.model.PublicUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.asset.getCompleteAssetOrNull
import com.wire.kalium.network.api.asset.getPreviewAssetOrNull
import com.wire.kalium.network.api.user.details.UserDetailsResponse

class PublicUserMapper {
    fun fromUserDetailResponse(userDetailResponse: UserDetailsResponse) = PublicUser(
        id = UserId(userDetailResponse.id.value, userDetailResponse.id.domain),
        name = userDetailResponse.name,
        handle = userDetailResponse.handle,
        accentId = userDetailResponse.accentId,
        team = userDetailResponse.team,
        previewPicture = userDetailResponse.assets.getPreviewAssetOrNull()?.key,
        completePicture = userDetailResponse.assets.getCompleteAssetOrNull()?.key,
    )
}
