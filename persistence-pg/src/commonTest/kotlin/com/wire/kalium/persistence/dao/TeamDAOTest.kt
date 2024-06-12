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

package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TeamDAOTest : BaseDatabaseTest() {

    private lateinit var teamDAO: TeamDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        teamDAO = db.teamDAO
    }

    @Test
    fun givenNoTeamsAreInserted_whenFetchingByTeamId_thenTheResultIsNull() = runTest {
        val result = teamDAO.getTeamById(teamId = teamId)
        assertNull(result.first())
    }

    @Test
    fun givenTeamIsInserted_whenFetchingTeamById_thenTheTeamIsReturned() = runTest {
        val insertedTeam = TeamEntity(id = teamId, name = "Test Team", icon = "icon")
        teamDAO.insertTeam(insertedTeam)

        val result = teamDAO.getTeamById(teamId = teamId)
        assertEquals(insertedTeam.id, result.first()?.id)
    }

    @Test
    fun givenMultipleTeamsAreInserted_whenFetchingEachTeamById_thenEachTeamIsReturned() = runTest {
        val insertedTeam1 = TeamEntity(id = "teamId 1", name = "Test Team 1", icon = "icon")
        val insertedTeam2 = TeamEntity(id = "teamId 2", name = "Test Team 2", icon = "icon")
        teamDAO.insertTeams(listOf(insertedTeam1, insertedTeam2))

        val resultTeam1 = teamDAO.getTeamById(teamId = "teamId 1")
        val resultTeam2 = teamDAO.getTeamById(teamId = "teamId 2")

        assertEquals(insertedTeam1.id, resultTeam1.first()?.id)
        assertEquals(insertedTeam2.id, resultTeam2.first()?.id)
    }

    @Test
    fun givenTeam_whenGetsUpdated_thenProperDataIsReturned() = runTest {
        val team = TeamEntity(id = "teamId 1", name = "Test Team 1", icon = "icon")
        teamDAO.insertTeam(team)
        val updatedTeam = team.copy(name = "Test Team 2")

        teamDAO.updateTeam(updatedTeam)
        val result = teamDAO.getTeamById(teamId = "teamId 1")

        assertEquals(updatedTeam, result.first())
    }

    private companion object {
        const val teamId = "abc-1234-def-5678-ghi-9012"
    }
}
