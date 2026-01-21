/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.mocks.mocks.user

import com.wire.kalium.mocks.mocks.domain.DomainMocks
import com.wire.kalium.network.api.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.model.AssetSizeDTO
import com.wire.kalium.network.api.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.api.model.SelfUserDTO
import com.wire.kalium.network.api.model.SupportedProtocolDTO
import com.wire.kalium.network.api.model.UserAssetDTO
import com.wire.kalium.network.api.model.UserAssetTypeDTO
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.api.model.UserTypeDTO

object UserMocks {

    val selfId = QualifiedID("selfId", DomainMocks.domain)
    val otherId = QualifiedID("otherId", DomainMocks.domain)
    val secondId = QualifiedID("secondId", DomainMocks.domain)
    val thirdId = QualifiedID("thirdId", DomainMocks.domain)

    val federatedId = QualifiedID("federatedId", DomainMocks.federatedDomain)
    val federatedSecondId = QualifiedID("federatedSecondId", DomainMocks.federatedDomain)

    val selfUser = SelfUserDTO(
        id = selfId,
        name = "selfUser",
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
        ssoID = null,
        supportedProtocols = null
    )

    val otherProfile = UserProfileDTO(
        id = otherId,
        name = "username",
        handle = "handle",
        email = "email",
        accentId = 0,
        legalHoldStatus = LegalHoldStatusDTO.DISABLED,
        teamId = "teamId",
        assets = listOf(
            UserAssetDTO("value1", AssetSizeDTO.PREVIEW, UserAssetTypeDTO.IMAGE),
            UserAssetDTO("value2", AssetSizeDTO.COMPLETE, UserAssetTypeDTO.IMAGE)
        ),
        deleted = false,
        expiresAt = null,
        nonQualifiedId = "",
        service = null,
        supportedProtocols = listOf(SupportedProtocolDTO.MLS),
        type = UserTypeDTO.REGULAR
    )

    val usersFoundList = listOf(otherProfile)

    val userList = ListUsersDTO(
        usersFailed = emptyList(),
        usersFound = usersFoundList
    )
}
