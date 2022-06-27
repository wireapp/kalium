package com.wire.kalium.logic.data.publicuser.model

import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType

data class OtherUser(
    override val id: UserId,
    val name: String?,
    val handle: String?,
    val email: String? = null,
    val phone: String? = null,
    val accentId: Int,
    val team: String?,
    val connectionStatus: ConnectionState = ConnectionState.NOT_CONNECTED,
    val previewPicture: UserAssetId?,
    val completePicture: UserAssetId?,
    val availabilityStatus: UserAvailabilityStatus,
    val userType: UserType
) : User()
