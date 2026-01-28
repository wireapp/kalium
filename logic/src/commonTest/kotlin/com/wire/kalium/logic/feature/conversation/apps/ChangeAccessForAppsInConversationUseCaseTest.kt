/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation.apps

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.feature.conversation.UpdateConversationAccessRoleUseCase
import com.wire.kalium.logic.framework.TestUser
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertIs

class ChangeAccessForAppsInConversationUseCaseTest {

    @Test
    fun givenSuccessfulUpdate_whenEnablingAppsAccess_thenSystemMessageIsInsertedWithEnabledTrue() = runTest {
        // Given
        val accessRoles = setOf(
            Conversation.AccessRole.TEAM_MEMBER,
            Conversation.AccessRole.NON_TEAM_MEMBER,
            Conversation.AccessRole.SERVICE
        )
        val access = setOf(Conversation.Access.INVITE, Conversation.Access.CODE)

        val (arrangement, changeAccessForApps) = Arrangement()
            .withUpdateAccessRoleReturning(UpdateConversationAccessRoleUseCase.Result.Success)
            .arrange()

        // When
        val result = changeAccessForApps(
            conversationId = MockConversation.ID,
            accessRoles = accessRoles,
            access = access
        )

        // Then
        assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)

        coVerify {
            arrangement.updateConversationAccessRole(
                conversationId = eq(MockConversation.ID),
                accessRoles = eq(accessRoles),
                access = eq(access)
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = any(),
                conversationId = eq(MockConversation.ID),
                senderUserId = eq(arrangement.selfUserId),
                isAppsAccessEnabled = eq(true)
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSuccessfulUpdate_whenDisablingAppsAccess_thenSystemMessageIsInsertedWithEnabledFalse() = runTest {
        // Given
        val accessRoles = setOf(
            Conversation.AccessRole.TEAM_MEMBER,
            Conversation.AccessRole.NON_TEAM_MEMBER
        )
        val access = setOf(Conversation.Access.INVITE)

        val (arrangement, changeAccessForApps) = Arrangement()
            .withUpdateAccessRoleReturning(UpdateConversationAccessRoleUseCase.Result.Success)
            .arrange()

        // When
        val result = changeAccessForApps(
            conversationId = MockConversation.ID,
            accessRoles = accessRoles,
            access = access
        )

        // Then
        assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)

        coVerify {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = any(),
                conversationId = eq(MockConversation.ID),
                senderUserId = eq(arrangement.selfUserId),
                isAppsAccessEnabled = eq(false)
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenFailedUpdate_whenChangingAppsAccess_thenNoSystemMessageIsInserted() = runTest {
        // Given
        val accessRoles = setOf(
            Conversation.AccessRole.TEAM_MEMBER,
            Conversation.AccessRole.SERVICE
        )
        val access = setOf(Conversation.Access.INVITE)
        val networkError = NetworkFailure.NoNetworkConnection(IOException())

        val (arrangement, changeAccessForApps) = Arrangement()
            .withUpdateAccessRoleReturning(UpdateConversationAccessRoleUseCase.Result.Failure(networkError))
            .arrange()

        // When
        val result = changeAccessForApps(
            conversationId = MockConversation.ID,
            accessRoles = accessRoles,
            access = access
        )

        // Then
        assertIs<UpdateConversationAccessRoleUseCase.Result.Failure>(result)
        assertIs<NetworkFailure.NoNetworkConnection>(result.cause)

        coVerify {
            arrangement.updateConversationAccessRole(
                conversationId = any(),
                accessRoles = any(),
                access = any()
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = any(),
                conversationId = any(),
                senderUserId = any(),
                isAppsAccessEnabled = any()
            )
        }.wasNotInvoked()
    }

    @Test
    fun givenSuccessfulUpdate_whenChangingAccess_thenCorrectAccessRolesArePassedToUpdateUseCase() = runTest {
        // Given
        val expectedAccessRoles = setOf(
            Conversation.AccessRole.TEAM_MEMBER,
            Conversation.AccessRole.NON_TEAM_MEMBER,
            Conversation.AccessRole.GUEST,
            Conversation.AccessRole.SERVICE
        )
        val expectedAccess = setOf(Conversation.Access.INVITE, Conversation.Access.CODE)

        val (arrangement, changeAccessForApps) = Arrangement()
            .withUpdateAccessRoleReturning(UpdateConversationAccessRoleUseCase.Result.Success)
            .arrange()

        // When
        changeAccessForApps(
            conversationId = MockConversation.ID,
            accessRoles = expectedAccessRoles,
            access = expectedAccess
        )

        // Then
        coVerify {
            arrangement.updateConversationAccessRole(
                conversationId = eq(MockConversation.ID),
                accessRoles = matches { it == expectedAccessRoles },
                access = matches { it == expectedAccess }
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSuccessfulUpdate_whenChangingAccess_thenSystemMessageContainsCorrectSender() = runTest {
        // Given
        val accessRoles = setOf(Conversation.AccessRole.SERVICE)
        val access = setOf(Conversation.Access.INVITE)

        val (arrangement, changeAccessForApps) = Arrangement()
            .withUpdateAccessRoleReturning(UpdateConversationAccessRoleUseCase.Result.Success)
            .arrange()

        // When
        changeAccessForApps(
            conversationId = MockConversation.ID,
            accessRoles = accessRoles,
            access = access
        )

        // Then
        coVerify {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = any(),
                conversationId = any(),
                senderUserId = matches { it == arrangement.selfUserId },
                isAppsAccessEnabled = any()
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSuccessfulUpdate_whenChangingAccess_thenSystemMessageContainsCorrectConversationId() = runTest {
        // Given
        val accessRoles = setOf(Conversation.AccessRole.SERVICE)
        val access = setOf(Conversation.Access.INVITE)

        val (arrangement, changeAccessForApps) = Arrangement()
            .withUpdateAccessRoleReturning(UpdateConversationAccessRoleUseCase.Result.Success)
            .arrange()

        // When
        changeAccessForApps(
            conversationId = MockConversation.ID,
            accessRoles = accessRoles,
            access = access
        )

        // Then
        coVerify {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = any(),
                conversationId = matches { it == MockConversation.ID },
                senderUserId = any(),
                isAppsAccessEnabled = any()
            )
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        val updateConversationAccessRole = mock(UpdateConversationAccessRoleUseCase::class)
        val systemMessageInserter = mock(SystemMessageInserter::class)
        val selfUserId = TestUser.SELF.id

        val changeAccessForApps = ChangeAccessForAppsInConversationUseCase(
            updateConversationAccessRole = updateConversationAccessRole,
            systemMessageInserter = systemMessageInserter,
            selfUserId = selfUserId
        )

        init {
            runBlocking {
                coEvery {
                    systemMessageInserter.insertConversationAppsAccessChanged(
                        eventId = any(),
                        conversationId = any(),
                        senderUserId = any(),
                        isAppsAccessEnabled = any()
                    )
                }.returns(Unit)
            }
        }

        suspend fun withUpdateAccessRoleReturning(result: UpdateConversationAccessRoleUseCase.Result) = apply {
            coEvery {
                updateConversationAccessRole(
                    conversationId = any(),
                    accessRoles = any(),
                    access = any()
                )
            }.returns(result)
        }

        fun arrange() = this to changeAccessForApps
    }
}
