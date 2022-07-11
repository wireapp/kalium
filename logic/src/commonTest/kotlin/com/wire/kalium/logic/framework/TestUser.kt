package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.network.api.model.AssetSizeDTO
import com.wire.kalium.network.api.model.UserAssetDTO
import com.wire.kalium.network.api.model.UserAssetTypeDTO
import com.wire.kalium.network.api.user.LegalHoldStatusResponse
import com.wire.kalium.network.api.user.details.UserProfileDTO
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity

object TestUser {
    val USER_ID = UserId("value", "domain")
    val ENTITY_ID = QualifiedIDEntity("entityUserValue", "entityDomain")
    val NETWORK_ID = com.wire.kalium.network.api.UserId(value = "networkValue", domain = "networkDomain")
    const val JSON_QUALIFIED_ID = """{"value":"jsonValue" , "domain":"jsonDomain" }"""

    val SELF = SelfUser(
        USER_ID,
        name = "username",
        handle = "handle",
        email = "email",
        phone = "phone",
        accentId = 0,
        teamId = TeamId("teamId"),
        connectionStatus = ConnectionState.ACCEPTED,
        previewPicture = UserAssetId("value1", "domain"),
        completePicture = UserAssetId("value2", "domain"),
        availabilityStatus = UserAvailabilityStatus.NONE
    )

    val OTHER = OtherUser(
        USER_ID.copy(value = "otherValue"),
        name = "otherUsername",
        handle = "otherHandle",
        email = "otherEmail",
        phone = "otherPhone",
        accentId = 0,
        teamId = TeamId("otherTeamId"),
        connectionStatus = ConnectionState.ACCEPTED,
        previewPicture = UserAssetId("value1", "domain"),
        completePicture = UserAssetId("value2", "domain"),
        availabilityStatus = UserAvailabilityStatus.NONE,
        userType = UserType.EXTERNAL
    )

    val ENTITY = UserEntity(
        id = ENTITY_ID,
        name = "username",
        handle = "handle",
        email = "email",
        phone = "phone",
        accentId = 0,
        team = "teamId",
        connectionStatus = ConnectionEntity.State.ACCEPTED,
        previewAssetId = QualifiedIDEntity("value1", "domain"),
        completeAssetId = QualifiedIDEntity("value2", "domain"),
        availabilityStatus = UserAvailabilityStatusEntity.NONE,
        userTypEntity = UserTypeEntity.EXTERNAL
    )

    val USER_PROFILE_DTO = UserProfileDTO(
        id = NETWORK_ID,
        name = "username",
        handle = "handle",
        email = "email",
        accentId = 0,
        legalHoldStatus = LegalHoldStatusResponse.DISABLED,
        teamId = "teamId",
        assets = listOf(
            UserAssetDTO("value1", AssetSizeDTO.PREVIEW, UserAssetTypeDTO.IMAGE),
            UserAssetDTO("value2", AssetSizeDTO.COMPLETE, UserAssetTypeDTO.IMAGE)
        ),
        deleted = false,
        expiresAt = null,
        nonQualifiedId = NETWORK_ID.value,
        service = null

    )
}
