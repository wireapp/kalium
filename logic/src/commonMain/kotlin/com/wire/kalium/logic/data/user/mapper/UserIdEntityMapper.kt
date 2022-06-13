package com.wire.kalium.logic.data.user.mapper

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.UserIDEntity as UserIdEntity

interface UserIdEntityMapper {
    fun toUserIdPersistence(userId: UserId): UserIdEntity
}

class UserIdEntityMapperImpl : UserIdEntityMapper {

    override fun toUserIdPersistence(userId: UserId) = UserIdEntity(userId.value, userId.domain)

}


