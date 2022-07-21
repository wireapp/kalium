package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
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
            userTypeEntity = UserTypeEntity.INTERNAL,
        )
        val (_, userMapper) = Arrangement().arrange()

        val result = userMapper.fromTeamMemberToDaoModel(
            teamId = TeamId("teamId"),
            teamMemberDTO = apiModel,
            userDomain = "userDomain"
        )

        assertEquals(expectedResult, result)
    }

    private class Arrangement {

        @Mock
        private val idMapper = mock(classOf<IdMapper>())

        private val userMapper = UserMapperImpl(idMapper = idMapper)

        init {
            given(idMapper)
                .function(idMapper::fromApiToDao)
                .whenInvokedWith(any())
                .then { TestUser.ENTITY_ID }
            given(idMapper)
                .function(idMapper::toQualifiedAssetIdEntity)
                .whenInvokedWith(any(), any())
                .then { value, _ -> PersistenceQualifiedId(value, TestUser.ENTITY_ID.domain) }
        }

        fun arrange() = this to userMapper
    }
}
