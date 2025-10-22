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

package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity

fun newUserEntity(id: String = "test") = newUserEntity(QualifiedIDEntity(id, "wire.com"), id)

fun newUserEntity(qualifiedID: QualifiedIDEntity, id: String = "test") =
    UserEntity(
        id = qualifiedID,
        name = "user$id",
        handle = "handle$id",
        email = "email$id",
        phone = "phone$id",
        accentId = 1,
        team = "team",
        ConnectionEntity.State.ACCEPTED,
        null,
        null,
        UserAvailabilityStatusEntity.NONE,
        UserTypeEntity.STANDARD,
        botService = null,
        deleted = false,
        hasIncompleteMetadata = false,
        expiresAt = null,
        defederated = false,
        supportedProtocols = setOf(SupportedProtocolEntity.PROTEUS),
        activeOneOnOneConversationId = null
    )

fun newUserDetailsEntity(id: String = "test") =
    UserDetailsEntity(
        id = QualifiedIDEntity(id, "wire.com"),
        name = "user$id",
        handle = "handle$id",
        email = "email$id",
        phone = "phone$id",
        accentId = 1,
        team = "team",
        ConnectionEntity.State.ACCEPTED,
        null,
        null,
        UserAvailabilityStatusEntity.NONE,
        UserTypeEntity.STANDARD,
        botService = null,
        deleted = false,
        hasIncompleteMetadata = false,
        expiresAt = null,
        defederated = false,
        isProteusVerified = false,
        supportedProtocols = setOf(SupportedProtocolEntity.PROTEUS),
        activeOneOnOneConversationId = null,
        isUnderLegalHold = false,
    )
