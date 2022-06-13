package com.wire.kalium.logic.data.user.mapper

import com.wire.kalium.logic.data.user.self.model.SelfUser
import com.wire.kalium.network.api.model.AssetSizeDTO
import com.wire.kalium.network.api.model.UserAssetDTO
import com.wire.kalium.network.api.model.UserAssetTypeDTO
import com.wire.kalium.network.api.user.self.UserUpdateRequest


/**
 * Maps the user data to be updated. if the parameters [newName] [newAccent] [newAssetId] are nulls,
 * it indicates that not updation should be made.
 *
 *  TODO(assets): handle deletion of assets references, emptyAssetList
 */
interface UserUpdateRequestMapper {
    fun fromModelToUpdateApiModel(
        selfUser: SelfUser,
        newName: String?,
        newAccent: Int?,
        newAssetId: String?
    ): UserUpdateRequest
}

class UserUpdateRequestMapperImpl : UserUpdateRequestMapper {

    override fun fromModelToUpdateApiModel(
        selfUser: SelfUser,
        newName: String?,
        newAccent: Int?,
        newAssetId: String?
    ): UserUpdateRequest {
        return UserUpdateRequest(
            name = newName, accentId = newAccent, assets = if (newAssetId != null) {
                listOf(
                    UserAssetDTO(newAssetId, AssetSizeDTO.COMPLETE, UserAssetTypeDTO.IMAGE),
                    UserAssetDTO(newAssetId, AssetSizeDTO.PREVIEW, UserAssetTypeDTO.IMAGE)
                )
            } else {
                null
            }
        )
    }

}
