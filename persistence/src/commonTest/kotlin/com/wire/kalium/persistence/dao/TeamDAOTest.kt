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

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        val db = createDatabase()
        teamDAO = db.teamDAO
    }

    @Test
    fun givenNoTeamsAreInserted_whenFetchingByTeamId_thenTheResultIsNull() = runTest {
        val result = teamDAO.getTeamById(teamId = teamId)
        assertNull(result.first())
    }

    @Test
    fun givenTeamIsInserted_whenFetchingTeamById_thenTheTeamIsReturned() = runTest {
        val insertedTeam = TeamEntity(id = teamId, name = "Test Team")
        teamDAO.insertTeam(insertedTeam)

        val result = teamDAO.getTeamById(teamId = teamId)
        assertEquals(insertedTeam.id, result.first()?.id)
    }

    @Test
    fun givenMultipleTeamsAreInserted_whenFetchingEachTeamById_thenEachTeamIsReturned() = runTest {
        val insertedTeam1 = TeamEntity(id = "teamId 1", name = "Test Team 1")
        val insertedTeam2 = TeamEntity(id = "teamId 2", name = "Test Team 2")
        teamDAO.insertTeams(listOf(insertedTeam1, insertedTeam2))

        val resultTeam1 = teamDAO.getTeamById(teamId = "teamId 1")
        val resultTeam2 = teamDAO.getTeamById(teamId = "teamId 2")

        assertEquals(insertedTeam1.id, resultTeam1.first()?.id)
        assertEquals(insertedTeam2.id, resultTeam2.first()?.id)
    }

    private companion object {
        val teamId = "abc-1234-def-5678-ghi-9012"
    }
}
