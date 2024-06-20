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

package com.wire.kalium.network.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ConversationAccessRoleDTO {
    @SerialName("team_member")
    TEAM_MEMBER,
    @SerialName("non_team_member")
    NON_TEAM_MEMBER,
    @SerialName("guest")
    GUEST,
    @SerialName("service")
    SERVICE,
    @SerialName("partner")
    EXTERNAL;

    override fun toString(): String {
        return this.name.lowercase()
    }

    companion object {
        val DEFAULT_VALUE_WHEN_NULL = setOf(TEAM_MEMBER, NON_TEAM_MEMBER, SERVICE)
    }
}
