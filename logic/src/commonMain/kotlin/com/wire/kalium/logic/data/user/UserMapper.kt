package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.user.details.UserDetailsResponse
import com.wire.kalium.network.api.user.self.ImageSize
import com.wire.kalium.network.api.user.self.SelfUserInfoResponse
import com.wire.kalium.network.api.user.self.UserAssetRequest
import com.wire.kalium.network.api.user.self.UserUpdateRequest
import com.wire.kalium.persistence.dao.User as PersistedUser

interface UserMapper {
    fun fromApiModel(selfUserInfoResponse: SelfUserInfoResponse): SelfUser
    fun fromApiModelToDaoModel(userDetailsResponse: UserDetailsResponse): PersistedUser
    fun fromApiModelToDaoModel(selfUserInfoResponse: SelfUserInfoResponse): PersistedUser
    fun fromDaoModel(user: PersistedUser): SelfUser
    fun fromModelToUpdateApiModel(user: SelfUser): UserUpdateRequest
}

internal class UserMapperImpl(private val idMapper: IdMapper) : UserMapper {

    override fun fromApiModel(selfUserInfoResponse: SelfUserInfoResponse): SelfUser {
        return SelfUser(idMapper.fromApiModel(selfUserInfoResponse.qualifiedId))
    }

    override fun fromApiModelToDaoModel(userDetailsResponse: UserDetailsResponse): PersistedUser {
        return PersistedUser(idMapper.fromApiToDao(userDetailsResponse.id), userDetailsResponse.name, userDetailsResponse.handle)
    }

    override fun fromDaoModel(user: com.wire.kalium.persistence.dao.User): SelfUser {
        return SelfUser(idMapper.fromDaoModel(user.id))
    }

    override fun fromModelToUpdateApiModel(user: SelfUser): UserUpdateRequest {
        // TODO: get data from mapped selfuser, should persist data first
        return UserUpdateRequest(
            user.id.value,
            idMapper.toApiModel(user.id),
            "Test User",
            listOf(
                UserAssetRequest("3-1-9594ddb9-2b55-4de3-90ef-ebe7c069da52", ImageSize.Complete),
                UserAssetRequest("3-1-9594ddb9-2b55-4de3-90ef-ebe7c069da52", ImageSize.Preview)
            ),
            1
        )
    }

    override fun fromApiModelToDaoModel(selfUserInfoResponse: SelfUserInfoResponse): PersistedUser {
        return PersistedUser(
            idMapper.fromApiToDao(selfUserInfoResponse.qualifiedId),
            selfUserInfoResponse.name,
            selfUserInfoResponse.handle
        )
    }
}
