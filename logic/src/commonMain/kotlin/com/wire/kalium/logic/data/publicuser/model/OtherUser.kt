package com.wire.kalium.logic.data.publicuser.model

import com.wire.kalium.logic.data.conversation.UserType
import com.wire.kalium.logic.data.id.QualifiedID
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

    fun determineUserType(selfUser: SelfUser): UserType {
        if (isUsingWireCloudBackEnd()) {
            if (areNotInTheSameTeam(selfUser)) {
                return UserType.GUEST
            }
        } else {
            if (areNotInTheSameTeam(selfUser)) {
                return UserType.FEDERATED
            }
        }

        return UserType.INTERNAL
    }

    private fun isUsingWireCloudBackEnd(): Boolean =
        id.domain.contains(QualifiedID.WIRE_PRODUCTION_DOMAIN)

    // if either self user has no team or other user,
    // does not make sense to compare them and we return false as of they are not on the same team
    private fun areNotInTheSameTeam(selfUser: SelfUser): Boolean =
        !(this.team != null && selfUser.team != null) || (this.team != selfUser.team)

}
