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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationGuestLink
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertIs

class UpdateConversationAccessUseCaseTest {

    @Test
    fun givenConversation_whenDisablingServices_thenUpdateAccessInfoIsCalledWithTheCorrectRoles() = runTest {
        val conversation = conversationStub.copy(
            accessRole = listOf(
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.NON_TEAM_MEMBER,
                Conversation.AccessRole.GUEST,
                Conversation.AccessRole.SERVICE
            ),
            access = listOf(Conversation.Access.INVITE, Conversation.Access.CODE),
        )

        val (arrangement, updateConversationAccess) = Arrangement()
            .withUpdateAccessInfoRetuning(Either.Right(Unit))
            .arrange()

        updateConversationAccess(
            conversationId = conversation.id,
            accessRoles = Conversation
                .accessRolesFor(
                    guestAllowed = true,
                    servicesAllowed = false,
                    nonTeamMembersAllowed = true
                ),
            access = Conversation.accessFor(guestsAllowed = true)
        ).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)
        }

        coVerify {
            arrangement
                .conversationRepository
                .updateAccessInfo(
                    conversation.id,
                    access = setOf(Conversation.Access.INVITE, Conversation.Access.CODE),
                    accessRole = Conversation
                        .defaultGroupAccessRoles
                        .toMutableSet()
                        .apply { add(Conversation.AccessRole.GUEST) }
                )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversation_whenEnablingServices_thenUpdateAccessInfoIsCalledWithTheCorrectRoles() = runTest {

        // Given a conversation where TEAM_MEMBER(s), NON_TEAM_MEMBER(s) and GUEST(s) but not SERVICE(s)
        val givenAccessRoles = Conversation
            .defaultGroupAccessRoles
            .toMutableList()
            .apply {
                add(Conversation.AccessRole.SERVICE)
            }

        // Given the access mode is CODE only
        val conversation = conversationStub.copy(
            accessRole = givenAccessRoles,
            access = Conversation.defaultGroupAccess.toMutableList().apply { add(Conversation.Access.INVITE) }
        )

        val (arrangement, updateConversationAccess) = Arrangement()
            .withUpdateAccessInfoRetuning(Either.Right(Unit))
            .arrange()

        updateConversationAccess(
            conversationId = conversation.id,
            accessRoles = Conversation
                .accessRolesFor(
                    guestAllowed = true,
                    servicesAllowed = true,
                    nonTeamMembersAllowed = true
                ),
            access = Conversation.accessFor(guestsAllowed = true)
        ).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)
        }

        coVerify {
            arrangement
                .conversationRepository
                .updateAccessInfo(
                    conversationID = conversation.id,
                    accessRole = setOf(
                        Conversation.AccessRole.TEAM_MEMBER,
                        Conversation.AccessRole.NON_TEAM_MEMBER,
                        Conversation.AccessRole.SERVICE,
                        Conversation.AccessRole.GUEST
                    ),
                    access = setOf(Conversation.Access.CODE, Conversation.Access.INVITE)
                )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversation_whenDisablingNonTeamMembers_thenUpdateAccessInfoIsCalledWithTheCorrectRoles() = runTest {
        val conversation = conversationStub.copy(
            accessRole = listOf(
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.NON_TEAM_MEMBER,
                Conversation.AccessRole.SERVICE,
                Conversation.AccessRole.GUEST
            )
        )

        val (arrangement, updateConversationAccess) = Arrangement()
            .withUpdateAccessInfoRetuning(Either.Right(Unit))
            .arrange()

        updateConversationAccess(
            conversationId = conversation.id,
            accessRoles = Conversation
                .accessRolesFor(
                    guestAllowed = true,
                    servicesAllowed = true,
                    nonTeamMembersAllowed = false
                ),
            Conversation.accessFor(guestsAllowed = true)
        ).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)
        }

        coVerify {
            arrangement.conversationRepository.updateAccessInfo(
                conversationID = conversation.id,
                accessRole = setOf(
                    Conversation.AccessRole.TEAM_MEMBER,
                    Conversation.AccessRole.SERVICE,
                    Conversation.AccessRole.GUEST
                ),
                access = conversation.access.toSet()
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversation_whenEnablingNonTeamMembers_thenUpdateAccessInfoIsCalledWithTheCorrectRoles() = runTest {
        val conversation = conversationStub.copy(
            accessRole = listOf(
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.SERVICE,
                Conversation.AccessRole.GUEST
            )
        )

        val (arrangement, updateConversationAccess) = Arrangement()
            .withUpdateAccessInfoRetuning(Either.Right(Unit)).arrange()

        updateConversationAccess(
            conversationId = conversation.id,
            accessRoles = Conversation
                .accessRolesFor(
                    guestAllowed = true,
                    servicesAllowed = true,
                    nonTeamMembersAllowed = true
                ),
            access = Conversation.accessFor(guestsAllowed = true)
        ).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)
        }

        coVerify {
            arrangement.conversationRepository.updateAccessInfo(
                conversationID = conversation.id,
                accessRole = setOf(
                    Conversation.AccessRole.TEAM_MEMBER,
                    Conversation.AccessRole.SERVICE,
                    Conversation.AccessRole.GUEST,
                    Conversation.AccessRole.NON_TEAM_MEMBER
                ),
                access = conversation.access.toSet()
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationAndGuestLink_whenDisablingGuests_thenRevokeGuestLinkAndUpdateAccessInfoAreCalledWithTheCorrectRoles() = runTest {
        val conversation = conversationStub.copy(
            accessRole = listOf(
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.NON_TEAM_MEMBER,
                Conversation.AccessRole.SERVICE,
                Conversation.AccessRole.GUEST
            ),
            access = listOf(Conversation.Access.INVITE)
        )

        val expected = ConversationGuestLink("guestLink", false)

        val (arrangement, updateConversationAccess) = Arrangement()
            .withUpdateAccessInfoRetuning(Either.Right(Unit))
            .withGuestRoomLink(flowOf(Either.Right(expected)))
            .arrange()

        updateConversationAccess(
            conversationId = conversation.id,
            accessRoles = Conversation
                .accessRolesFor(
                    guestAllowed = false,
                    servicesAllowed = true,
                    nonTeamMembersAllowed = true
                ),
            access = Conversation.accessFor(guestsAllowed = false)
        ).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)
        }

        coVerify {
            arrangement.conversationGroupRepository.revokeGuestRoomLink(any())
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationRepository.updateAccessInfo(
                conversationID = conversation.id,
                accessRole = setOf(
                    Conversation.AccessRole.TEAM_MEMBER,
                    Conversation.AccessRole.NON_TEAM_MEMBER,
                    Conversation.AccessRole.SERVICE
                ),
                access = Conversation.defaultGroupAccess
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversation_whenEnablingGuests_thenUpdateAccessInfoIsCalledWithTheCorrectRoles() = runTest {

        // Given a conversation where TEAM_MEMBER(s), NON_TEAM_MEMBER(s) and SERVICE(s) have access and GUEST(s) don't
        val givenAccessRoles = Conversation
            .defaultGroupAccessRoles
            .toMutableList()
            .apply {
                add(Conversation.AccessRole.SERVICE)
            }

        // Given the access mode is CODE only
        val conversation = conversationStub.copy(
            accessRole = givenAccessRoles,
            access = Conversation.defaultGroupAccess.toList()
        )

        // Given
        val (arrangement, updateConversationAccess) = Arrangement()
            .withUpdateAccessInfoRetuning(Either.Right(Unit))
            .arrange()

        // When Guests are allowed
        updateConversationAccess(
            conversationId = conversation.id,
            accessRoles = Conversation.accessRolesFor(guestAllowed = true, servicesAllowed = true, nonTeamMembersAllowed = true),
            access = Conversation.accessFor(guestsAllowed = true)
        ).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)
        }

        // Then
        coVerify {
            arrangement.conversationRepository.updateAccessInfo(
                conversationID = conversation.id,
                accessRole = Conversation.defaultGroupAccessRoles.toMutableSet().apply {
                    add(Conversation.AccessRole.GUEST)
                    add(Conversation.AccessRole.SERVICE)
                },
                access = setOf(Conversation.Access.INVITE, Conversation.Access.CODE)
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenError_whenCallingUpdateAccessInfo_thenFailureIsPropagated() = runTest {
        val conversation = conversationStub

        val expected: ConversationGuestLink? = null
        val (arrangement, updateConversationAccess) = Arrangement()
            .withUpdateAccessInfoRetuning(Either.Left(NetworkFailure.NoNetworkConnection(IOException())))
            .withGuestRoomLink(flowOf(Either.Right(expected)))
            .arrange()

        // allowGuest = false, allowServices = true, allowNonTeamMember = true
        updateConversationAccess(
            conversationId = conversation.id,
            accessRoles = Conversation.accessRolesFor(guestAllowed = false, servicesAllowed = true, nonTeamMembersAllowed = true),
            access = Conversation.accessFor(guestsAllowed = false)
        ).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Failure>(result)
            assertIs<NetworkFailure.NoNetworkConnection>(result.cause)
        }

        coVerify {
            arrangement.conversationRepository.updateAccessInfo(any(), any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAnGuestAccessRole_whenInvokingUpdateAccessInfo_thenRevokeGuestLinkShouldNotBeInvoked() = runTest {
        val accessRoles = setOf(Conversation.AccessRole.GUEST)

        val expected = ConversationGuestLink("guestLink", false)

        val (arrangement, updateConversationAccessRole) = Arrangement()
            .withWaitUntilLiveOrFailure(Either.Right(Unit))
            .withGuestRoomLink(flowOf(Either.Right(expected)))
            .withUpdateAccessInfoRetuning(Either.Right(Unit))
            .arrange()

        val result = updateConversationAccessRole(TestConversation.ID, accessRoles, setOf())
        assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)

        coVerify {
            arrangement.conversationGroupRepository.revokeGuestRoomLink(any())
        }.wasNotInvoked()

    }

    @Test
    fun givenAnAccessRoleWithoutGuestAndSyncFailing_whenInvokingUpdateAccessInfo_thenRevokeGuestLinkShouldNotBeInvoked() = runTest {
        val accessRoles = setOf(Conversation.AccessRole.TEAM_MEMBER)

        val expected = ConversationGuestLink("guestLink", false)

        val (arrangement, updateConversationAccessRole) = Arrangement()
            .withWaitUntilLiveOrFailure(Either.Left(CoreFailure.Unknown(RuntimeException("Error"))))
            .withGuestRoomLink(flowOf(Either.Right(expected)))
            .withUpdateAccessInfoRetuning(Either.Right(Unit))
            .arrange()

        val result = updateConversationAccessRole(TestConversation.ID, accessRoles, setOf())
        assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)

        coVerify {
            arrangement.conversationGroupRepository.revokeGuestRoomLink(any())
        }.wasNotInvoked()
    }

    companion object {
        val conversationStub = Conversation(
            ConversationId(value = "someId", domain = "someDomain"),
            "GROUP Conversation",
            Conversation.Type.Group.Regular,
            TeamId("someTeam"),
            ProtocolInfo.Proteus,
            MutedConversationStatus.AllAllowed,
            null,
            null,
            null,
            access = listOf(
                Conversation.Access.CODE,
                Conversation.Access.INVITE
            ),
            accessRole = listOf(
                Conversation.AccessRole.NON_TEAM_MEMBER,
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.GUEST
            ),
            lastReadDate = Instant.UNIX_FIRST_DATE,
            creatorId = "someCreatorId",
            receiptMode = Conversation.ReceiptMode.DISABLED,
            messageTimer = null,
            userMessageTimer = null,
            archived = false,
            archivedDateTime = null,
            mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
        )
    }

    private class Arrangement {
                val conversationRepository = mock(ConversationRepository::class)
        val conversationGroupRepository = mock(ConversationGroupRepository::class)
        val syncManager = mock(SyncManager::class)

        val updateConversationAccess: UpdateConversationAccessRoleUseCase = UpdateConversationAccessRoleUseCase(
            conversationRepository,
            conversationGroupRepository,
            syncManager
        )

        init {
            runBlocking {
                coEvery {
                    syncManager.waitUntilLiveOrFailure()
                }.returns(Either.Right(Unit))

                coEvery {
                    conversationGroupRepository.revokeGuestRoomLink(any())
                }.returns(Either.Right(Unit))
            }
        }

        suspend fun withUpdateAccessInfoRetuning(either: Either<CoreFailure, Unit>) = apply {
            coEvery {
                conversationRepository.updateAccessInfo(any(), any(), any())
            }.returns(either)
        }

        suspend fun withWaitUntilLiveOrFailure(either: Either<CoreFailure, Unit>) = apply {
            coEvery {
                syncManager.waitUntilLiveOrFailure()
            }.returns(either)
        }

        suspend fun withGuestRoomLink(result: Flow<Either<CoreFailure, ConversationGuestLink?>>) = apply {
            coEvery {
                conversationGroupRepository.observeGuestRoomLink(any())
            }.returns(result)
        }

        fun arrange() = this to updateConversationAccess
    }
}
