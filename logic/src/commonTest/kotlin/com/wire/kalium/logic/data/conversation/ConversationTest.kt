package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.framework.TestConversation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConversationTest {

    @Test
    fun givenConversationWithGuestAccessRole_thenIsGuestAllowedIsTrue() {
        val conversation = TestConversation.CONVERSATION.copy(accessRole = listOf(Conversation.AccessRole.GUEST))
        assertTrue(conversation.isGuestAllowed())
    }

    @Test
    fun givenConversationWithNoGuestAccessRole_thenIsGuestAllowedIsFalse() {
        val conversation = TestConversation.CONVERSATION.copy(accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER))
        assertFalse(conversation.isGuestAllowed())
    }

    @Test
    fun givenConversationWithinTeamMemberAccessRole_thenIsNonTeamMemberAllowedIsTrue() {
        val conversation = TestConversation.CONVERSATION.copy(accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER))
        assertTrue(conversation.isNonTeamMemberAllowed())
    }

    @Test
    fun givenConversationWithNoTeamMemberAccessRole_thenIsNonTeamMemberAllowedIsFalse() {
        val conversation = TestConversation.CONVERSATION.copy(accessRole = listOf(Conversation.AccessRole.TEAM_MEMBER))
        assertFalse(conversation.isNonTeamMemberAllowed())
    }

    @Test
    fun givenConversationWithServiceAccessRole_thenIsNonTeamMemberAllowedIsTrue() {
        val conversation = TestConversation.CONVERSATION.copy(accessRole = listOf(Conversation.AccessRole.SERVICE))
        assertTrue(conversation.isServicesAllowed())
    }

    @Test
    fun givenConversationWithNoServiceAccessRole_thenIsNonTeamMemberAllowedIsFalse() {
        val conversation = TestConversation.CONVERSATION.copy(accessRole = listOf(Conversation.AccessRole.TEAM_MEMBER))
        assertFalse(conversation.isServicesAllowed())
    }
}
