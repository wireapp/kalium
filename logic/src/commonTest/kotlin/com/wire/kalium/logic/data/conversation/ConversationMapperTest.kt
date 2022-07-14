package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.conversation.ConvProtocol
import com.wire.kalium.network.api.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.MutedStatus
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationMapperTest {

    @Mock
    val idMapper = mock(classOf<IdMapper>())

    @Mock
    val protocolInfoMapper = mock(classOf<ProtocolInfoMapper>())

    @Mock
    val conversationStatusMapper = mock(classOf<ConversationStatusMapper>())

    private lateinit var conversationMapper: ConversationMapper

    @BeforeTest
    fun setup() {
        conversationMapper = ConversationMapperImpl(idMapper, conversationStatusMapper, protocolInfoMapper)
    }

    @Test
    fun givenAConversationResponse_whenMappingFromConversationResponse_thenTheNameShouldBeCorrect() {
        val response = CONVERSATION_RESPONSE
        val transformedConversationId = QualifiedIDEntity("transformed", "tDomain")

        given(idMapper)
            .function(idMapper::fromApiToDao)
            .whenInvokedWith(any())
            .then { transformedConversationId }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromDaoModel)
            .whenInvokedWith(any())
            .then { MutedConversationStatus.AllAllowed }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromApiToDaoModel)
            .whenInvokedWith(any())
            .then { ConversationEntity.MutedStatus.ALL_ALLOWED }

        val mappedResponse = conversationMapper.fromApiModelToDaoModel(response, mlsGroupState = null, SELF_USER_TEAM_ID)

        assertEquals(mappedResponse.name, response.name)
    }

    @Test
    fun givenAConversationResponse_whenMappingFromConversationResponseToDaoModel_thenShouldCallIdMapperToMapConversationId() {
        val response = CONVERSATION_RESPONSE
        val originalConversationId = ORIGINAL_CONVERSATION_ID
        val transformedConversationId = QualifiedIDEntity("transformed", "tDomain")

        given(idMapper)
            .function(idMapper::fromApiToDao)
            .whenInvokedWith(any())
            .then { transformedConversationId }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromDaoModel)
            .whenInvokedWith(any())
            .then { MutedConversationStatus.AllAllowed }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromApiToDaoModel)
            .whenInvokedWith(any())
            .then { ConversationEntity.MutedStatus.ALL_ALLOWED }

        conversationMapper.fromApiModelToDaoModel(response, mlsGroupState = null, SELF_USER_TEAM_ID)

        verify(idMapper)
            .invocation { idMapper.fromApiToDao(originalConversationId) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAFakeTeamOneOnOneConversationResponse_whenMappingFromConversationResponseToDaoModel_thenShouldMapToOneOnOneConversation() {
        val response = CONVERSATION_RESPONSE.copy(
            // Looks like a Group
            type = ConversationResponse.Type.GROUP,
            // No Name
            name = null,
            // Only one other participant
            members = CONVERSATION_RESPONSE.members.copy(otherMembers = listOf(OTHER_MEMBERS.first())),
            // Same team as user
            teamId = SELF_USER_TEAM_ID.value
        )

        given(idMapper)
            .function(idMapper::fromApiToDao)
            .whenInvokedWith(any())
            .then { QualifiedIDEntity("transformed", "tDomain") }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromDaoModel)
            .whenInvokedWith(any())
            .then { MutedConversationStatus.AllAllowed }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromApiToDaoModel)
            .whenInvokedWith(any())
            .then { ConversationEntity.MutedStatus.ALL_ALLOWED }

        val result = conversationMapper.fromApiModelToDaoModel(response, mlsGroupState = null, SELF_USER_TEAM_ID)

        assertEquals(ConversationEntity.Type.ONE_ON_ONE, result.type)
    }

    @Test
    fun givenAGroupConversationResponseWithoutName_whenMappingFromConversationResponseToDaoModel_thenShouldMapToGroupType() {
        val response = CONVERSATION_RESPONSE.copy(type = ConversationResponse.Type.GROUP, name = null)

        given(idMapper)
            .function(idMapper::fromApiToDao)
            .whenInvokedWith(any())
            .then { QualifiedIDEntity("transformed", "tDomain") }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromDaoModel)
            .whenInvokedWith(any())
            .then { MutedConversationStatus.AllAllowed }

        given(conversationStatusMapper)
            .function(conversationStatusMapper::fromApiToDaoModel)
            .whenInvokedWith(any())
            .then { ConversationEntity.MutedStatus.ALL_ALLOWED }

        val result = conversationMapper.fromApiModelToDaoModel(response, mlsGroupState = null, SELF_USER_TEAM_ID)

        assertEquals(ConversationEntity.Type.GROUP, result.type)
    }

    private companion object {
        val ORIGINAL_CONVERSATION_ID = ConversationId("original", "oDomain")
        val SELF_USER_TEAM_ID = TeamId("teamID")
        val SELF_MEMBER_RESPONSE =
            ConversationMemberDTO.Self(
                id = UserId("selfId", "selfDomain"),
                conversationRole = "wire_member",
                otrMutedRef = "2022-04-11T20:24:57.237Z",
                otrMutedStatus = MutedStatus.ALL_ALLOWED
            )
        val OTHER_MEMBERS =
            listOf(ConversationMemberDTO.Other(service = null, id = UserId("other1", "domain1"), conversationRole = "wire_admin"))
        val MEMBERS_RESPONSE = ConversationMembersResponse(SELF_MEMBER_RESPONSE, OTHER_MEMBERS)
        val CONVERSATION_RESPONSE = ConversationResponse(
            "creator",
            MEMBERS_RESPONSE,
            "name",
            ORIGINAL_CONVERSATION_ID,
            null,
            ConversationResponse.Type.GROUP,
            null,
            null,
            ConvProtocol.PROTEUS,
            lastEventTime = "2022-03-30T15:36:00.000Z",
            access = setOf(ConversationAccessDTO.INVITE, ConversationAccessDTO.CODE),
            accessRole = setOf(
                ConversationAccessRoleDTO.GUEST,
                ConversationAccessRoleDTO.TEAM_MEMBER,
                ConversationAccessRoleDTO.NON_TEAM_MEMBER
            )
        )
    }
}
