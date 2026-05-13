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
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertIs

class ChangeAccessForAppsInConversationUseCaseTest {

    @Test
    fun givenSuccessfulUpdate_whenEnablingAppsAccess_thenSystemMessageIsInsertedWithEnabledTrue() = runTest {
        val accessRoles = setOf(
            Conversation.AccessRole.TEAM_MEMBER,
            Conversation.AccessRole.NON_TEAM_MEMBER,
            Conversation.AccessRole.SERVICE
        )
        val access = setOf(Conversation.Access.INVITE, Conversation.Access.CODE)

        val (arrangement, changeAccessForApps) = Arrangement()
            .withUpdateAccessRoleReturning(UpdateConversationAccessRoleUseCase.Result.Success)
            .arrange()

        val result = changeAccessForApps(
            conversationId = MockConversation.ID,
            accessRoles = accessRoles,
            access = access
        )

        assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationAccessRole(
                conversationId = eq(MockConversation.ID),
                accessRoles = eq(accessRoles),
                access = eq(access)
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = any(),
                conversationId = eq(MockConversation.ID),
                senderUserId = eq(arrangement.selfUserId),
                isAppsAccessEnabled = eq(true)
            )
        }
    }

    @Test
    fun givenSuccessfulUpdate_whenDisablingAppsAccess_thenSystemMessageIsInsertedWithEnabledFalse() = runTest {
        val accessRoles = setOf(
            Conversation.AccessRole.TEAM_MEMBER,
            Conversation.AccessRole.NON_TEAM_MEMBER
        )
        val access = setOf(Conversation.Access.INVITE)

        val (arrangement, changeAccessForApps) = Arrangement()
            .withUpdateAccessRoleReturning(UpdateConversationAccessRoleUseCase.Result.Success)
            .arrange()

        val result = changeAccessForApps(
            conversationId = MockConversation.ID,
            accessRoles = accessRoles,
            access = access
        )

        assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = any(),
                conversationId = eq(MockConversation.ID),
                senderUserId = eq(arrangement.selfUserId),
                isAppsAccessEnabled = eq(false)
            )
        }
    }

    @Test
    fun givenFailedUpdate_whenChangingAppsAccess_thenNoSystemMessageIsInserted() = runTest {
        val accessRoles = setOf(
            Conversation.AccessRole.TEAM_MEMBER,
            Conversation.AccessRole.SERVICE
        )
        val access = setOf(Conversation.Access.INVITE)
        val networkError = NetworkFailure.NoNetworkConnection(IOException())

        val (arrangement, changeAccessForApps) = Arrangement()
            .withUpdateAccessRoleReturning(UpdateConversationAccessRoleUseCase.Result.Failure(networkError))
            .arrange()

        val result = changeAccessForApps(
            conversationId = MockConversation.ID,
            accessRoles = accessRoles,
            access = access
        )

        assertIs<UpdateConversationAccessRoleUseCase.Result.Failure>(result)
        assertIs<NetworkFailure.NoNetworkConnection>(result.cause)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationAccessRole(
                conversationId = any(),
                accessRoles = any(),
                access = any()
            )
        }

        verifySuspend(VerifyMode.not) {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = any(),
                conversationId = any(),
                senderUserId = any(),
                isAppsAccessEnabled = any()
            )
        }
    }

    @Test
    fun givenSuccessfulUpdate_whenChangingAccess_thenCorrectAccessRolesArePassedToUpdateUseCase() = runTest {
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

        changeAccessForApps(
            conversationId = MockConversation.ID,
            accessRoles = expectedAccessRoles,
            access = expectedAccess
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationAccessRole(
                conversationId = eq(MockConversation.ID),
                accessRoles = matching { it == expectedAccessRoles },
                access = matching { it == expectedAccess }
            )
        }
    }

    @Test
    fun givenSuccessfulUpdate_whenChangingAccess_thenSystemMessageContainsCorrectSender() = runTest {
        val accessRoles = setOf(Conversation.AccessRole.SERVICE)
        val access = setOf(Conversation.Access.INVITE)

        val (arrangement, changeAccessForApps) = Arrangement()
            .withUpdateAccessRoleReturning(UpdateConversationAccessRoleUseCase.Result.Success)
            .arrange()

        changeAccessForApps(
            conversationId = MockConversation.ID,
            accessRoles = accessRoles,
            access = access
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = any(),
                conversationId = any(),
                senderUserId = matching { it == arrangement.selfUserId },
                isAppsAccessEnabled = any()
            )
        }
    }

    @Test
    fun givenSuccessfulUpdate_whenChangingAccess_thenSystemMessageContainsCorrectConversationId() = runTest {
        val accessRoles = setOf(Conversation.AccessRole.SERVICE)
        val access = setOf(Conversation.Access.INVITE)

        val (arrangement, changeAccessForApps) = Arrangement()
            .withUpdateAccessRoleReturning(UpdateConversationAccessRoleUseCase.Result.Success)
            .arrange()

        changeAccessForApps(
            conversationId = MockConversation.ID,
            accessRoles = accessRoles,
            access = access
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = any(),
                conversationId = matching { it == MockConversation.ID },
                senderUserId = any(),
                isAppsAccessEnabled = any()
            )
        }
    }

    private class Arrangement {
        val updateConversationAccessRole = mock<UpdateConversationAccessRoleUseCase>(mode = MockMode.autoUnit)
        val systemMessageInserter = mock<SystemMessageInserter>(mode = MockMode.autoUnit)
        val selfUserId = TestUser.SELF.id

        val changeAccessForApps = ChangeAccessForAppsInConversationUseCase(
            updateConversationAccessRole = updateConversationAccessRole,
            systemMessageInserter = systemMessageInserter,
            selfUserId = selfUserId
        )

        init {
            runBlocking {
                everySuspend {
                    systemMessageInserter.insertConversationAppsAccessChanged(
                        eventId = any(),
                        conversationId = any(),
                        senderUserId = any(),
                        isAppsAccessEnabled = any()
                    )
                } returns Unit
            }
        }

        suspend fun withUpdateAccessRoleReturning(result: UpdateConversationAccessRoleUseCase.Result) = apply {
            everySuspend {
                updateConversationAccessRole(
                    conversationId = any(),
                    accessRoles = any(),
                    access = any()
                )
            } returns result
        }

        fun arrange() = this to changeAccessForApps
    }
}
