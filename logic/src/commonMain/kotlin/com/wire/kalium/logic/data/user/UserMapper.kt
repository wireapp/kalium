package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.user.self.SelfUserInfoResponse
import com.wire.kalium.persistence.dao.User as PersistedUser

interface UserMapper {
    fun fromApiModel(selfUserInfoResponse: SelfUserInfoResponse): SelfUser
    fun fromApiModelToDaoModel(selfUserInfoResponse: SelfUserInfoResponse): PersistedUser
    fun fromDaoModel(user: PersistedUser): SelfUser
}

internal class UserMapperImpl(private val idMapper: IdMapper) : UserMapper {

    override fun fromApiModel(selfUserInfoResponse: SelfUserInfoResponse): SelfUser {
        return SelfUser(idMapper.fromApiModel(selfUserInfoResponse.qualifiedId))
    }

    override fun fromDaoModel(user: com.wire.kalium.persistence.dao.User): SelfUser {
        return SelfUser(idMapper.fromDaoModel(user.id))
    }

    override fun fromApiModelToDaoModel(selfUserInfoResponse: SelfUserInfoResponse): PersistedUser {
        return PersistedUser(
            idMapper.fromApiToDao(selfUserInfoResponse.qualifiedId),
            selfUserInfoResponse.name,
            selfUserInfoResponse.handle)
    }

}
