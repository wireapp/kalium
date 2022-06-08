package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.conversation.UserType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.ConversationEntity
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs


class GetOrCreateOneToOneConversationUseCaseTest {

    @Mock
    private val conversationRepository = mock(classOf<ConversationRepository>())

    private lateinit var getOrCreateOneToOneConversationUseCase: GetOrCreateOneToOneConversationUseCase

    @BeforeTest
    fun setUp() {
        getOrCreateOneToOneConversationUseCase = GetOrCreateOneToOneConversationUseCase(
            conversationRepository = conversationRepository
        )
    }

    @Test
    fun givenConversationDoesNotExist_whenCallingTheUseCase_ThenDoNotCreateAConversationButReturnExisting() = runTest {
        //given
        given(conversationRepository)
            .suspendFunction(conversationRepository::getOneToOneConversationDetailsByUserId)
            .whenInvokedWith(anything())
            .thenReturn(Either.Right(null))

        given(conversationRepository)
            .suspendFunction(conversationRepository::createGroupConversation)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(Either.Right(CONVERSATION))
        //when
        val result = getOrCreateOneToOneConversationUseCase.invoke(USER_ID)
        //then
        assertIs<CreateConversationResult.Success>(result)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::createGroupConversation)
            .with(anything(), anything(), anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationExist_whenCallingTheUseCase_ThenCreateAConversationAndReturn() = runTest {
        //given
        given(conversationRepository)
            .coroutine { getOneToOneConversationDetailsByUserId(USER_ID) }
            .then { Either.Right(CONVERSATION_DETAILS) }
        //when
        val result = getOrCreateOneToOneConversationUseCase.invoke(USER_ID)
        //then
        assertIs<CreateConversationResult.Success>(result)

        verify(conversationRepository)
            .coroutine { createGroupConversation(members = MEMBER) }
            .wasNotInvoked()
    }

    private companion object {
        val USER_ID = UserId(value = "userId", domain = "domainId")
        val MEMBER = listOf(Member(USER_ID))
        val CONVERSATION_ID = ConversationId(value = "userId", domain = "domainId")
        val CONVERSATION = Conversation(
            id = CONVERSATION_ID,
            name = null,
            type = Conversation.Type.ONE_ON_ONE,
            teamId = null,
            MutedConversationStatus.AllAllowed,
            null,
            null
        )
        val OTHER_USER = OtherUser(
            id =
            USER_ID,
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 0,
            team = null,
            previewPicture = null,
            completePicture = null,
            availabilityStatus = UserAvailabilityStatus.NONE
        )
        val CONVERSATION_DETAILS = ConversationDetails.OneOne(
            CONVERSATION,
            OTHER_USER,
            ConnectionState.ACCEPTED,
            LegalHoldStatus.ENABLED,
            UserType.INTERNAL
        )
    }

}
