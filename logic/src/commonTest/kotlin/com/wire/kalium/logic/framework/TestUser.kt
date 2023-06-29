/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.base.model.AssetSizeDTO
import com.wire.kalium.network.api.base.model.LegalHoldStatusResponse
import com.wire.kalium.network.api.base.model.SelfUserDTO
import com.wire.kalium.network.api.base.model.UserAssetDTO
import com.wire.kalium.network.api.base.model.UserAssetTypeDTO
import com.wire.kalium.network.api.base.model.UserProfileDTO
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity

object TestUser {
    private const val value = "value"
    private const val domain = "domain"

    val USER_ID = UserId(value, domain)
    val OTHER_USER_ID = USER_ID.copy(value = "otherValue")
    val OTHER_USER_ID_2 = USER_ID.copy(value = "otherValue2")
    val ENTITY_ID = QualifiedIDEntity(value, domain)
    val NETWORK_ID = com.wire.kalium.network.api.base.model.UserId(
        value = value,
        domain = domain
    )
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
        OTHER_USER_ID,
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
        userType = UserType.EXTERNAL,
        botService = null,
        deleted = false
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
        previewAssetId = QualifiedIDEntity("value1", ENTITY_ID.domain),
        completeAssetId = QualifiedIDEntity("value2", ENTITY_ID.domain),
        availabilityStatus = UserAvailabilityStatusEntity.NONE,
        userType = UserTypeEntity.EXTERNAL,
        botService = null,
        deleted = false,
        expiresAt = null
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

    val SELF_USER_DTO = SelfUserDTO(
        id = NETWORK_ID,
        name = "user_name_123",
        accentId = 2,
        assets = listOf(),
        deleted = null,
        email = null,
        handle = null,
        service = null,
        teamId = null,
        expiresAt = "",
        nonQualifiedId = "",
        locale = "",
        managedByDTO = null,
        phone = null,
        ssoID = null
    )

    val LIST_USERS_DTO = ListUsersDTO(
        usersFailed = emptyList(),
        usersFound = listOf(USER_PROFILE_DTO)
    )
}
