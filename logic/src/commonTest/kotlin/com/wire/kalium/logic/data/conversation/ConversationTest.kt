/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import kotlin.test.assertEquals
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

    @Test
    fun givenACombinationOfFlags_thenTheSetOfAccessRolesIsCorrect() {

        var accessRoles = Conversation.accessRolesFor(guestAllowed = false, servicesAllowed = false, nonTeamMembersAllowed = false)
        assertEquals(setOf(Conversation.AccessRole.TEAM_MEMBER), accessRoles)

        accessRoles = Conversation.accessRolesFor(guestAllowed = true, servicesAllowed = false, nonTeamMembersAllowed = false)
        assertEquals(
            setOf(Conversation.AccessRole.TEAM_MEMBER, Conversation.AccessRole.GUEST),
            accessRoles
        )

        accessRoles = Conversation.accessRolesFor(guestAllowed = true, servicesAllowed = true, nonTeamMembersAllowed = false)
        assertEquals(
            setOf(Conversation.AccessRole.TEAM_MEMBER, Conversation.AccessRole.GUEST, Conversation.AccessRole.SERVICE),
            accessRoles
        )

        accessRoles = Conversation.accessRolesFor(guestAllowed = true, servicesAllowed = true, nonTeamMembersAllowed = true)
        assertEquals(
            setOf(
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.GUEST,
                Conversation.AccessRole.SERVICE,
                Conversation.AccessRole.NON_TEAM_MEMBER
            ),
            accessRoles
        )

        accessRoles = Conversation.accessRolesFor(guestAllowed = false, servicesAllowed = true, nonTeamMembersAllowed = false)
        assertEquals(
            setOf(
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.SERVICE,
            ),
            accessRoles
        )

        accessRoles = Conversation.accessRolesFor(guestAllowed = false, servicesAllowed = true, nonTeamMembersAllowed = true)
        assertEquals(
            setOf(
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.SERVICE,
                Conversation.AccessRole.NON_TEAM_MEMBER
            ),
            accessRoles
        )

        accessRoles = Conversation.accessRolesFor(guestAllowed = false, servicesAllowed = false, nonTeamMembersAllowed = true)
        assertEquals(
            setOf(
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.NON_TEAM_MEMBER
            ),
            accessRoles
        )

        accessRoles = Conversation.accessRolesFor(guestAllowed = true, servicesAllowed = false, nonTeamMembersAllowed = true)
        assertEquals(
            setOf(
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.NON_TEAM_MEMBER,
                Conversation.AccessRole.GUEST
                ),
            accessRoles
        )
    }

    @Test
    fun givenACombinationOfFlags_thenTheSetOfAccessIsCorrect() {
        var access = Conversation.accessFor(false)
        assertEquals(
            setOf(
                Conversation.Access.INVITE,
            ),
            access
        )

        access = Conversation.accessFor(true)
        assertEquals(
            setOf(
                Conversation.Access.INVITE,
                Conversation.Access.CODE,
                ),
            access
        )
    }
}
