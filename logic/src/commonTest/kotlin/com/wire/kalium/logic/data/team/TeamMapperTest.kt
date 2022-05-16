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
            name = "teamName"
        )

        val expectedResult = TeamEntity(id = "teamId", name = "teamName")

        val result = teamMapper.fromDtoToEntity(apiModel)

        assertEquals(expectedResult, result)
    }
}
