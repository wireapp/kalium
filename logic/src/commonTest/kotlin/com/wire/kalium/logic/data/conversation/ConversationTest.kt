/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
