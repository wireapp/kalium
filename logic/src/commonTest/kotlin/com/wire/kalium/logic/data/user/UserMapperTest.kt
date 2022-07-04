package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
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
        val apiModel = TestTeam.memberDTO(
            nonQualifiedUserId = "teamMember1"
        )

        val expectedResult = UserEntity(
            id = QualifiedIDEntity(
                value = "teamMember1",
                domain = "userDomain"
            ),
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 1,
            team = "teamId",
            connectionStatus = ConnectionEntity.State.ACCEPTED,
            previewAssetId = null,
            completeAssetId = null,
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userTypEntity = UserTypeEntity.INTERNAL,
        )

        val result = userMapper.fromTeamMemberToDaoModel(
            teamId = TeamId("teamId"),
            teamMemberDTO = apiModel,
            userDomain = "userDomain"
        )

        assertEquals(expectedResult, result)
    }
}
