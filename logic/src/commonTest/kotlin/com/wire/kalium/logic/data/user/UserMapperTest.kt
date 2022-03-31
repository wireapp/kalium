package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.utils.generator.TeamGenerator
import com.wire.kalium.persistence.dao.QualifiedIDEntity
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
        val apiModel = TeamGenerator.createTeamMember(
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
