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

package com.wire.kalium.logic.data.team

import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.persistence.dao.TeamEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamMapperTest {

    private lateinit var teamMapper: TeamMapper

    @BeforeTest
    fun setUp() {
        teamMapper = TeamMapperImpl()
    }

    @Test
    fun givenTeamApiModel_whenMappingFromApiResponse_thenDaoModelIsReturned() = runTest {
        val apiModel = TestTeam.dto(
            id = "teamId",
            name = "teamName",
            icon = "icon"
        )

        val expectedResult = TeamEntity(id = "teamId", name = "teamName", "icon")

        val result = teamMapper.fromDtoToEntity(apiModel)

        assertEquals(expectedResult, result)
    }

    @Test
    fun givenTeamModel_whenMappingFromLogicModel_thenDaoModelIsReturned() = runTest {
        val model = Team(
            id = "teamId",
            name = "teamName",
            icon = "icon"
        )

        val expectedResult = TeamEntity(id = "teamId", name = "teamName", icon = "icon")

        val result = teamMapper.fromModelToEntity(model)

        assertEquals(expectedResult, result)
    }

    @Test
    fun givenTeamApiEntity_whenMappingDao_thenLogicModelIsReturned() = runTest {
        val apiModel = TeamEntity(
            id = "teamId",
            name = "teamName",
            icon = "icon"
        )

        val expectedResult = Team(id = "teamId", name = "teamName", icon = "icon")

        val result = teamMapper.fromDaoModelToTeam(apiModel)

        assertEquals(expectedResult, result)
    }
}
