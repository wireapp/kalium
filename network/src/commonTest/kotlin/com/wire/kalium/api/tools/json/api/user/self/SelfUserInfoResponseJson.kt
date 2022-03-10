package com.wire.kalium.api.tools.json.api.user.self

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.model.AssetSizeDTO
import com.wire.kalium.network.api.model.UserAssetDTO
import com.wire.kalium.network.api.model.UserAssetTypeDTO
import com.wire.kalium.network.api.user.self.ManagedBy
import com.wire.kalium.network.api.user.self.SelfUserInfoResponse

object SelfUserInfoResponseJson {
    private val jsonProvider = { serializable: SelfUserInfoResponse ->
        """
        |{
        |  "id": "${serializable.id}",
        |  "qualified_id": {
        |       "id": "${serializable.qualifiedId.value}"
        |       "domain": "${serializable.qualifiedId.domain}"
        |  },
        |  "name": "${serializable.name}",
        |  "accent_id": ${serializable.accentId},
        |  "assets": [
        |       {
        |           "key": "${serializable.assets[0].key}",
        |           "type": "${serializable.assets[0].type}",
        |           "size": "${serializable.assets[0].size}"
        |       }
        |  ],
        |  "locale": "${serializable.locale}",
        |  "team": "${serializable.team}",
        |  "managed_by": "${serializable.managedBy}"
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        SelfUserInfoResponse(
            email = null,
            phone = null,
            userSsoId = null,
            id = "id",
            qualifiedId = UserId(value = "99db9768-04e3-4b5d-9268-831b6a25c4ab", domain = "domain"),
            name = "cool_name",
            accentId = 0,
            deleted = null,
            assets = listOf(UserAssetDTO(type = UserAssetTypeDTO.IMAGE, key = "asset_key", size = AssetSizeDTO.COMPLETE)),
            locale = "user_local",
            service = null,
            expiresAt = null,
            handle = null,
            team = "99db9768-04e3-4b5d-9268-831b6a25c4ab",
            managedBy = ManagedBy.Wire
        ),
        jsonProvider
    )
}
