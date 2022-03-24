package com.wire.kalium.logic.data.wireuser.model

import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserId

data class WireUser(
    override val id: UserId,
    val name: String?,
    val handle: String?,
    val email: String? = null,
    val phone: String? = null,
    val accentId: Int,
    val team: String?,
    val previewPicture: UserAssetId?,
    val completePicture: UserAssetId?
) : User()
