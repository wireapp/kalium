package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.asset.UploadedAssetId
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
    fun fromModelToUpdateApiModel(user: SelfUser, uploadedAssetId: UploadedAssetId): UserUpdateRequest
}

internal class UserMapperImpl(private val idMapper: IdMapper) : UserMapper {

    override fun fromApiModel(selfUserInfoResponse: SelfUserInfoResponse): SelfUser {
        return SelfUser(
            idMapper.fromApiModel(selfUserInfoResponse.qualifiedId),
            selfUserInfoResponse.name,
            selfUserInfoResponse.handle,
            selfUserInfoResponse.email,
            selfUserInfoResponse.phone,
            selfUserInfoResponse.accentId,
            selfUserInfoResponse.team,
            emptyList()
        )
    }

    override fun fromApiModelToDaoModel(userDetailsResponse: UserDetailsResponse): PersistedUser {
        return PersistedUser(
            idMapper.fromApiToDao(userDetailsResponse.id),
            userDetailsResponse.name,
            userDetailsResponse.handle,
            null,
            null,
            userDetailsResponse.accentId,
            null
        )
    }

    override fun fromDaoModel(user: com.wire.kalium.persistence.dao.User) =
        SelfUser(idMapper.fromDaoModel(user.id), user.name, user.handle, user.email, user.phone, user.accentId, user.team, emptyList())

    override fun fromModelToUpdateApiModel(user: SelfUser, uploadedAssetId: UploadedAssetId): UserUpdateRequest {
        return UserUpdateRequest(
            user.id.value,
            idMapper.toApiModel(user.id),
            user.name,
            listOf(UserAssetRequest(uploadedAssetId.key, ImageSize.Complete), UserAssetRequest(uploadedAssetId.key, ImageSize.Preview)),
            user.accentId
        )
    }

    override fun fromApiModelToDaoModel(selfUserInfoResponse: SelfUserInfoResponse): PersistedUser {
        return PersistedUser(
            idMapper.fromApiToDao(selfUserInfoResponse.qualifiedId),
            selfUserInfoResponse.name,
            selfUserInfoResponse.handle,
            selfUserInfoResponse.email,
            selfUserInfoResponse.phone,
            selfUserInfoResponse.accentId,
            selfUserInfoResponse.team
        )
    }
}
