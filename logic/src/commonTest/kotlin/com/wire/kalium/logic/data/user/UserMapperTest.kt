package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.network.api.user.LegalHoldStatusResponse
import com.wire.kalium.persistence.dao.QualifiedID
import com.wire.kalium.persistence.dao.UserEntity
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UserMapperTest {

    private lateinit var userMapper: UserMapper

    @Mock
    private val idMapper = mock(classOf<IdMapper>())

    @BeforeTest
    fun setUp() {
        userMapper = UserMapperImpl(idMapper = idMapper)
    }

    @Test
    fun givenTeamMemberApiModel_whenMappingFromApiResponse_thenDaoModelIsReturned() = runTest {
        val apiModel = TeamsApi.TeamMember(
            nonQualifiedUserId = "teamMember1",
            createdAt = "01011970",
            legalHoldStatus = LegalHoldStatusResponse.NO_CONSENT,
            createdBy = "nonQualiefiedUserId1",
            permissions = TeamsApi.Permissions(copy = 1, own = 1)
        )

        val expectedResult = UserEntity(
            id = QualifiedID(
                value = "teamMember1",
                domain = "userDomain"
            ),
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 1,
            team = "teamId",
            previewAssetId = null,
            completeAssetId = null
        )

        val result = userMapper.fromTeamMemberToDaoModel(
            teamId = "teamId",
            teamMember = apiModel,
            userDomain = "userDomain"
        )

        assertEquals(expectedResult, result)
    }
}
