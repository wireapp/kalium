package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.conversation.UserType
import com.wire.kalium.logic.data.publicuser.model.OtherUser

interface UserTypeMapper {
    fun fromOtherUserAndSelfUser(otherUser: OtherUser, selfUser: SelfUser): UserType
}

class UserTypeMapperImpl : UserTypeMapper {

    @Suppress("ReturnCount")
    override fun fromOtherUserAndSelfUser(otherUser: OtherUser, selfUser: SelfUser): UserType {
        if (otherUser.isUsingWireCloudBackEnd()) {
            if (areNotInTheSameTeam(otherUser, selfUser)) {
                return UserType.GUEST
            }
        } else {
            if (areNotInTheSameTeam(otherUser, selfUser)) {
                return UserType.FEDERATED
            }
        }

        return UserType.INTERNAL
    }

    // if either self user has no team or other user,
    // does not make sense to compare them and we return false as of they are not on the same team
    private fun areNotInTheSameTeam(otherUser: OtherUser, selfUser: SelfUser): Boolean =
        !(otherUser.team != null && selfUser.team != null) || (otherUser.team != selfUser.team)

}
