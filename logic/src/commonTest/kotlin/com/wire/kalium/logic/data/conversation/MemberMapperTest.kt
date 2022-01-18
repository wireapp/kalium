package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.conversation.ConversationOtherMembersResponse
import com.wire.kalium.network.api.conversation.ConversationSelfMemberResponse
import io.mockative.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberMapperTest {

    @Mock
    val idMapper = mock(classOf<IdMapper>())

    private lateinit var memberMapper: MemberMapper

    @BeforeTest
    fun setup() {
        memberMapper = MemberMapperImpl(idMapper)
    }

    @Test
    fun givenAMembersResponse_whenMappingFromApiModel_shouldCallIdMapperForAllMembers() {
        val membersResponse = MEMBERS_RESPONSE
        val mappedID = QualifiedID("someValue", "someDomain")
        given(idMapper)
            .function(idMapper::fromApiModel)
            .whenInvokedWith(any())
            .then { mappedID }

        memberMapper.fromApiModel(membersResponse)

        verify(idMapper)
            .invocation { idMapper.fromApiModel(SELF_MEMBER_RESPONSE.userId) }
            .wasInvoked(exactly = once)

        verify(idMapper)
            .invocation { idMapper.fromApiModel(OTHER_MEMBER.userId) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAMembersResponse_whenMappingFromApiModel_shouldUseIdReturnedFromMapperAllMembers() {
        val membersResponse = MEMBERS_RESPONSE
        val mappedID = QualifiedID("someValue", "someDomain")
        given(idMapper)
            .function(idMapper::fromApiModel)
            .whenInvokedWith(any())
            .then { mappedID }

        val result = memberMapper.fromApiModel(membersResponse)

        assertEquals(mappedID, result.otherMembers.first().id)
        assertEquals(mappedID, result.self.id)
    }

    @Test
    fun givenAMembersResponseWithNoOthers_whenMappingFromApiModel_shouldReturnNoOthers() {
        val membersResponse = MEMBERS_RESPONSE.copy(otherMembers = listOf())
        val mappedID = QualifiedID("someValue", "someDomain")
        given(idMapper)
            .function(idMapper::fromApiModel)
            .whenInvokedWith(any())
            .then { mappedID }

        val result = memberMapper.fromApiModel(membersResponse)

        assertTrue(result.otherMembers.isEmpty())
    }

    @Test @Suppress("MagicNumber")
    fun givenAMembersResponseWithMultipleOthers_whenMappingFromApiModel_shouldReturnMultipleOthers() {
        val others = MEMBERS_RESPONSE.otherMembers.toMutableList()
        repeat(42){
            others.add(others.first())
        }
        val membersResponse = MEMBERS_RESPONSE.copy(otherMembers = others)
        val mappedID = QualifiedID("someValue", "someDomain")
        given(idMapper)
            .function(idMapper::fromApiModel)
            .whenInvokedWith(any())
            .then { mappedID }

        val result = memberMapper.fromApiModel(membersResponse)

        assertEquals(others.size, result.otherMembers.size)
    }

    private companion object {
        val SELF_MEMBER_RESPONSE = ConversationSelfMemberResponse(UserId("selfId", "selfDomain"))
        val OTHER_MEMBER = ConversationOtherMembersResponse(null, UserId("other1", "domain1"))
        val MEMBERS_RESPONSE = ConversationMembersResponse(SELF_MEMBER_RESPONSE, listOf(OTHER_MEMBER))
    }
}
