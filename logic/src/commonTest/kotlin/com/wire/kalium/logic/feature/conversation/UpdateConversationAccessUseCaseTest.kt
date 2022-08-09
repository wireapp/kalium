package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateConversationAccessUseCaseTest {

    @Test
    fun givenConversation_whenDisablingServices_thenUpdateAccessInfoIsCalledWithTheCorrectRoles() = runTest {
        val conversation = conversationStub.copy(
            accessRole = listOf(
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.NON_TEAM_MEMBER,
                Conversation.AccessRole.GUEST,
                Conversation.AccessRole.SERVICE
            )
        )

        val (arrangement, updateConversationAccess) = Arrangement().withDetailsByIdReturning(Either.Right(conversation))
            .withUpdateAccessInfoRetuning(Either.Right(Unit)).arrange()

        updateConversationAccess(conversation.id, allowGuest = true, allowNonTeamMember = true, allowServices = false).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)
        }

        verify(arrangement.conversationRepository).coroutine { arrangement.conversationRepository.detailsById(conversation.id) }
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository).coroutine {
            arrangement.conversationRepository.updateAccessInfo(
                conversation.id, conversation.access, accessRole = listOf(
                    Conversation.AccessRole.TEAM_MEMBER, Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST
                )
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversation_whenEnablingServices_thenUpdateAccessInfoIsCalledWithTheCorrectRoles() = runTest {
        val conversation = conversationStub.copy(
            accessRole = listOf(
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.NON_TEAM_MEMBER,
                Conversation.AccessRole.SERVICE,
                Conversation.AccessRole.GUEST

            )
        )

        val (arrangement, updateConversationAccess) = Arrangement().withDetailsByIdReturning(Either.Right(conversation))
            .withUpdateAccessInfoRetuning(Either.Right(Unit)).arrange()

        updateConversationAccess(conversation.id, allowGuest = true, allowNonTeamMember = true, allowServices = true).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)
        }
        verify(arrangement.conversationRepository).coroutine { arrangement.conversationRepository.detailsById(conversation.id) }
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository).coroutine {
            arrangement.conversationRepository.updateAccessInfo(
                conversation.id, conversation.access, accessRole = listOf(
                    Conversation.AccessRole.TEAM_MEMBER,
                    Conversation.AccessRole.NON_TEAM_MEMBER,
                    Conversation.AccessRole.SERVICE,
                    Conversation.AccessRole.GUEST
                )
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

        val (arrangement, updateConversationAccess) = Arrangement().withDetailsByIdReturning(Either.Right(conversation))
            .withUpdateAccessInfoRetuning(Either.Right(Unit)).arrange()

        updateConversationAccess(conversation.id, allowGuest = true, allowNonTeamMember = false, allowServices = true).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)
        }

        verify(arrangement.conversationRepository).coroutine { arrangement.conversationRepository.detailsById(conversation.id) }
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository).coroutine {
            arrangement.conversationRepository.updateAccessInfo(
                conversation.id, conversation.access, accessRole = listOf(
                    Conversation.AccessRole.TEAM_MEMBER, Conversation.AccessRole.SERVICE, Conversation.AccessRole.GUEST

                )
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversation_whenEnablingNonTeamMembers_thenUpdateAccessInfoIsCalledWithTheCorrectRoles() = runTest {
        val conversation = conversationStub.copy(
            accessRole = listOf(
                Conversation.AccessRole.TEAM_MEMBER, Conversation.AccessRole.SERVICE, Conversation.AccessRole.GUEST
            )
        )

        val (arrangement, updateConversationAccess) = Arrangement().withDetailsByIdReturning(Either.Right(conversation))
            .withUpdateAccessInfoRetuning(Either.Right(Unit)).arrange()

        updateConversationAccess(conversation.id, allowGuest = true, allowNonTeamMember = true, allowServices = true).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)
        }

        verify(arrangement.conversationRepository).coroutine { arrangement.conversationRepository.detailsById(conversation.id) }
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository).coroutine {
            arrangement.conversationRepository.updateAccessInfo(
                conversation.id, conversation.access, accessRole = listOf(
                    Conversation.AccessRole.TEAM_MEMBER,
                    Conversation.AccessRole.SERVICE,
                    Conversation.AccessRole.GUEST,
                    Conversation.AccessRole.NON_TEAM_MEMBER
                )
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversation_whenDisablingGuests_thenUpdateAccessInfoIsCalledWithTheCorrectRoles() = runTest {
        val conversation = conversationStub.copy(
            accessRole = listOf(
                Conversation.AccessRole.TEAM_MEMBER,
                Conversation.AccessRole.NON_TEAM_MEMBER,
                Conversation.AccessRole.SERVICE,
                Conversation.AccessRole.GUEST
            )
        )

        val (arrangement, updateConversationAccess) = Arrangement().withDetailsByIdReturning(Either.Right(conversation))
            .withUpdateAccessInfoRetuning(Either.Right(Unit)).arrange()

        updateConversationAccess(conversation.id, allowGuest = false, allowNonTeamMember = true, allowServices = true).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)
        }

        verify(arrangement.conversationRepository).coroutine { arrangement.conversationRepository.detailsById(conversation.id) }
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository).coroutine {
            arrangement.conversationRepository.updateAccessInfo(
                conversation.id, conversation.access, accessRole = listOf(
                    Conversation.AccessRole.TEAM_MEMBER, Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.SERVICE
                )
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConversation_whenEnablingGuests_thenUpdateAccessInfoIsCalledWithTheCorrectRoles() = runTest {
        val conversation = conversationStub.copy(
            accessRole = listOf(
                Conversation.AccessRole.TEAM_MEMBER, Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.SERVICE
            )
        )

        val (arrangement, updateConversationAccess) = Arrangement().withDetailsByIdReturning(Either.Right(conversation))
            .withUpdateAccessInfoRetuning(Either.Right(Unit)).arrange()

        updateConversationAccess(conversation.id, allowGuest = false, allowNonTeamMember = true, allowServices = true).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Success>(result)
        }

        verify(arrangement.conversationRepository).coroutine { arrangement.conversationRepository.detailsById(conversation.id) }
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository).coroutine {
            arrangement.conversationRepository.updateAccessInfo(
                conversation.id, conversation.access, accessRole = listOf(
                    Conversation.AccessRole.TEAM_MEMBER, Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.SERVICE
                )
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenError_whenCallingDetailsById_thenFailureIsPropagated() = runTest {
        val conversation = conversationStub

        val (arrangement, updateConversationAccess) = Arrangement().withDetailsByIdReturning(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        updateConversationAccess(conversation.id, allowGuest = false, allowNonTeamMember = true, allowServices = true).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Failure>(result)
            assertEquals(StorageFailure.DataNotFound, result.cause)
        }

        verify(arrangement.conversationRepository).suspendFunction(arrangement.conversationRepository::detailsById).with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository).suspendFunction(arrangement.conversationRepository::updateAccessInfo)
            .with(any(), any(), any()).wasNotInvoked()
    }

    @Test
    fun givenError_whenCallingUpdateAccessInfo_thenFailureIsPropagated() = runTest {
        val conversation = conversationStub

        val (arrangement, updateConversationAccess) = Arrangement().withDetailsByIdReturning(Either.Right(conversation))
            .withUpdateAccessInfoRetuning(Either.Left(NetworkFailure.NoNetworkConnection(IOException()))).arrange()

        updateConversationAccess(conversation.id, allowGuest = false, allowNonTeamMember = true, allowServices = true).also { result ->
            assertIs<UpdateConversationAccessRoleUseCase.Result.Failure>(result)
            assertIs<NetworkFailure.NoNetworkConnection>(result.cause)
        }

        verify(arrangement.conversationRepository).suspendFunction(arrangement.conversationRepository::detailsById).with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository).suspendFunction(arrangement.conversationRepository::updateAccessInfo)
            .with(any(), any(), any()).wasInvoked(exactly = once)
    }

    companion object {
        val conversationStub = Conversation(
            ConversationId(value = "1O1 ID", domain = "conv domain"),
            "ONE_ON_ONE Name",
            Conversation.Type.ONE_ON_ONE,
            TeamId("cool_team_123"),
            ProtocolInfo.Proteus,
            MutedConversationStatus.AllAllowed,
            null,
            null,
            null,
            access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
            accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST),
            lastReadDate = "2022.01.02"
        )
    }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        val updateConversationAccess: UpdateConversationAccessRoleUseCase = UpdateConversationAccessRoleUseCase(conversationRepository)

        fun withDetailsByIdReturning(either: Either<StorageFailure, Conversation>) = apply {
            given(conversationRepository).suspendFunction(conversationRepository::detailsById).whenInvokedWith(any()).thenReturn(either)
        }

        fun withUpdateAccessInfoRetuning(either: Either<CoreFailure, Unit>) = apply {
            given(conversationRepository).suspendFunction(conversationRepository::updateAccessInfo).whenInvokedWith(any(), any(), any())
                .thenReturn(either)
        }

        fun arrange() = this to updateConversationAccess
    }
}
