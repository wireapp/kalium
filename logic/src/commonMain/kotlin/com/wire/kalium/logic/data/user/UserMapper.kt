package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.user.details.UserDetailsResponse
import com.wire.kalium.network.api.user.self.ImageSize
import com.wire.kalium.network.api.user.self.SelfUserInfoResponse
import com.wire.kalium.network.api.asset.UserAssetDTO
import com.wire.kalium.network.api.user.self.UserUpdateRequest
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserId as UserIdEntity

interface UserMapper {
    fun fromApiModel(selfUserInfoResponse: SelfUserInfoResponse): SelfUser
    fun fromApiModelToDaoModel(userDetailsResponse: UserDetailsResponse): UserEntity
    fun fromApiModelToDaoModel(selfUserInfoResponse: SelfUserInfoResponse): UserEntity
    fun fromDaoModel(user: UserEntity): SelfUser
    /**
     * Maps the user data to be updated. if the parameters [newName] [newAccent] [newAssetId] are nulls,
     * it indicates that not updation should be made.
     *
     *  TODO: handle deletion of assets references, emptyAssetList
     */
    fun fromModelToUpdateApiModel(user: SelfUser, newName: String?, newAccent: Int?, newAssetId: String?): UserUpdateRequest
    fun fromUpdateRequestToDaoModel(user: SelfUser, updateRequest: UserUpdateRequest): UserEntity
    fun toUserIdPersistence(userId: UserId): UserIdEntity
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

    override fun fromApiModelToDaoModel(userDetailsResponse: UserDetailsResponse): UserEntity {
        return UserEntity(
            idMapper.fromApiToDao(userDetailsResponse.id),
            userDetailsResponse.name,
            userDetailsResponse.handle,
            null,
            null,
            userDetailsResponse.accentId,
            null
        )
    }

    override fun fromDaoModel(user: UserEntity) =
        SelfUser(idMapper.fromDaoModel(user.id), user.name, user.handle, user.email, user.phone, user.accentId, user.team, emptyList())

    override fun fromModelToUpdateApiModel(
        user: SelfUser,
        newName: String?,
        newAccent: Int?,
        newAssetId: String?
    ): UserUpdateRequest {
        return UserUpdateRequest(
            id = user.id.value,
            qualifiedId = idMapper.toApiModel(user.id),
            name = newName,
            accentId = newAccent,
            assets = if (newAssetId != null) {
                listOf(
                    UserAssetDTO(newAssetId, ImageSize.Complete),
                    UserAssetDTO(newAssetId, ImageSize.Preview)
                )
            } else {
                null
            }
        )
    }

    override fun fromUpdateRequestToDaoModel(user: SelfUser, updateRequest: UserUpdateRequest): UserEntity {
        return UserEntity(
            id = idMapper.toDaoModel(user.id),
            name = updateRequest.name ?: user.name,
            handle = user.handle,
            email = user.email,
            phone = user.phone,
            accentId = updateRequest.accentId ?: user.accentId,
            team = user.team
        )
    }

    override fun fromApiModelToDaoModel(selfUserInfoResponse: SelfUserInfoResponse): UserEntity {
        return UserEntity(
            idMapper.fromApiToDao(selfUserInfoResponse.qualifiedId),
            selfUserInfoResponse.name,
            selfUserInfoResponse.handle,
            selfUserInfoResponse.email,
            selfUserInfoResponse.phone,
            selfUserInfoResponse.accentId,
            selfUserInfoResponse.team
        )
    }

    override fun toUserIdPersistence(userId: UserId) = UserIdEntity(userId.value, userId.domain)
}
