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

package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.network.api.base.model.NonQualifiedUserId
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.base.model.TeamDTO
import com.wire.kalium.persistence.dao.TeamEntity
import com.wire.kalium.network.api.base.model.TeamId as TeamIdDTO

@Suppress("LongParameterList")
object TestTeam {
    val TEAM: Team = Team(id = "Some-team", name = "Some-name", icon = "icon")
    val TEAM_ID = TeamId("Some-team")
    val TEAM_DTO = dto(
        id = "Some-team",
        name = "Some-name"
    )
    val TEAM_ENTITY = TeamEntity(id = "Some-team", name = "Some-name", "icon")

    fun dto(
        creator: String = "creator",
        icon: String = "icon",
        name: String = "Some-name",
        id: TeamIdDTO = "Some-Team",
        iconKey: String? = null,
        binding: Boolean? = false
    ): TeamDTO = TeamDTO(
        creator = creator,
        icon = icon,
        name = name,
        id = id,
        iconKey = iconKey,
        binding = binding
    )

    fun memberDTO(
        nonQualifiedUserId: NonQualifiedUserId = "id",
        createdBy: NonQualifiedUserId? = null,
        legalHoldStatus: LegalHoldStatusDTO? = null,
        createdAt: String? = null,
        permissions: TeamsApi.Permissions? = null
    ): TeamsApi.TeamMemberDTO = TeamsApi.TeamMemberDTO(
        nonQualifiedUserId = nonQualifiedUserId,
        createdBy = createdBy,
        legalHoldStatus = legalHoldStatus,
        createdAt = createdAt,
        permissions = permissions
    )
}
