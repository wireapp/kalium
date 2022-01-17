package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.conversation.ConversationOtherMembersResponse
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.ConversationSelfMemberResponse
import io.mockative.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationMapperTest {

    @Mock
    val idMapper = mock(classOf<IdMapper>())

    @Mock
    val memberMapper = mock(classOf<MemberMapper>())

    private lateinit var conversationMapper: ConversationMapper

    @BeforeTest
    fun setup() {
        conversationMapper = ConversationMapperImpl(idMapper, memberMapper)
    }

    @Test
    fun givenAConversationResponse_whenMappingFromConversationResponse_thenShouldCallIdMapperToMapConversationId() {
        val response = CONVERSATION_RESPONSE
        val originalConversationId = ORIGINAL_CONVERSATION_ID
        val transformedConversationId = QualifiedID("transformed", "tDomain")

        given(idMapper)
            .function(idMapper::fromApiModel)
            .whenInvokedWith(any())
            .then { transformedConversationId }

        val mappedResponse = conversationMapper.fromApiModel(response)

        verify(idMapper)
            .invocation { idMapper.fromApiModel(originalConversationId) }
            .wasInvoked(exactly = once)

        assertEquals(transformedConversationId, mappedResponse.id)
    }

    @Test
    fun givenAConversationResponse_whenMappingFromConversationResponse_thenShouldCallMemberMapperToMapMembers() {
        val response = CONVERSATION_RESPONSE
        val originalConversationId = ORIGINAL_CONVERSATION_ID
        val self = Member(QualifiedID("whatever", "domain"))
        val otherMembers = listOf(Member(QualifiedID("v1", "d1")), Member(QualifiedID("v2", "d2")))
        val transformedMemberInfo = MembersInfo(self, otherMembers)

        given(memberMapper)
            .function(memberMapper::fromApiModel)
            .whenInvokedWith(any())
            .then { transformedMemberInfo }

        conversationMapper.fromApiModel(response)

        verify(idMapper)
            .invocation { idMapper.fromApiModel(originalConversationId) }
            .wasInvoked(exactly = once)
    }

    private companion object {
        val ORIGINAL_CONVERSATION_ID = ConversationId("original", "oDomain")
        val SELF_MEMBER_RESPONSE = ConversationSelfMemberResponse(UserId("selfId", "selfDomain"))
        val OTHER_MEMBERS = listOf(ConversationOtherMembersResponse(null, UserId("other1", "domain1")))
        val MEMBERS_RESPONSE = ConversationMembersResponse(SELF_MEMBER_RESPONSE, OTHER_MEMBERS)
        val CONVERSATION_RESPONSE = ConversationResponse(
            "creator", MEMBERS_RESPONSE, "name", ORIGINAL_CONVERSATION_ID, 42, null
        )

    }
}
