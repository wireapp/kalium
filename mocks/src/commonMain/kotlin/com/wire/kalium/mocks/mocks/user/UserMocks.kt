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
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.api.model.SelfUserDTO

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
}
