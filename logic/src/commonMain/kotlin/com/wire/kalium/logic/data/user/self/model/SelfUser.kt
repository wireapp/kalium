package com.wire.kalium.logic.data.user.self.model

import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId

data class SelfUser(
    override val id: UserId,
    val name: String?,
    val handle: String?,
    val email: String?,
    val phone: String?,
    val accentId: Int,
    val team: String?,
    val connectionStatus: ConnectionState,
    val previewPicture: UserAssetId?,
    val completePicture: UserAssetId?,
    val availabilityStatus: UserAvailabilityStatus
) : User()
