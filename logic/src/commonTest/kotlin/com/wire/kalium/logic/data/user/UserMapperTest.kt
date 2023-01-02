package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.team.TeamRole
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UserMapperTest {

    @Test
    fun givenUserProfileDTOAndUserTypeEntity_whenMappingFromApiResponse_thenDaoModelIsReturned() = runTest {
        // Given
        val givenResponse = TestUser.USER_PROFILE_DTO
        val givenUserTypeEntity = UserTypeEntity.EXTERNAL
        val expectedResult = TestUser.ENTITY.copy(
            phone = null, // UserProfileDTO doesn't contain the phone
            connectionStatus = ConnectionEntity.State.NOT_CONNECTED // UserProfileDTO doesn't contain the connection status
        )
        val (_, userMapper) = Arrangement().arrange()
        // When
        val result = userMapper.fromApiModelWithUserTypeEntityToDaoModel(givenResponse, givenUserTypeEntity)
        // Then
        assertEquals(expectedResult, result)
    }

    @Test
    fun givenTeamMemberApiModel_whenMappingFromApiResponse_thenDaoModelIsReturned() = runTest {
        val apiModel = TestTeam.memberDTO(
            nonQualifiedUserId = "teamMember1",
            permissions = TeamsApi.Permissions(TeamRole.Member.value, TeamRole.Member.value)
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
            userType = UserTypeEntity.STANDARD,
            botService = null,
            deleted = false
        )
        val (_, userMapper) = Arrangement().arrange()

        val result = userMapper.fromTeamMemberToDaoModel(
            teamId = TeamId("teamId"),
            userDomain = "userDomain",
            nonQualifiedUserId = "teamMember1",
            permissionCode = apiModel.permissions?.own
        )

        assertEquals(expectedResult, result)
    }

    private class Arrangement {

        @Mock
        private val idMapper = mock(classOf<IdMapper>())

        private val userMapper = UserMapperImpl(idMapper = idMapper)

        fun arrange() = this to userMapper
    }
}
