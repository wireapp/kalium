package com.wire.kalium.logic.data.publicuser.model

import com.wire.kalium.logic.data.conversation.UserType
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserId

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
    val completePicture: UserAssetId?
) : User() {

    fun isUsingWireCloudBackEnd(): Boolean =
        id.domain.contains("wire.com")

}
