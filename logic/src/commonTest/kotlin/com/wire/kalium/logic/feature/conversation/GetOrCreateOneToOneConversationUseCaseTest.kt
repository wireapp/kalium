package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.conversation.ProtocolInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
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
        // given
        given(conversationRepository)
            .suspendFunction(conversationRepository::getOneToOneConversationDetailsByUserId)
            .whenInvokedWith(anything())
            .thenReturn(Either.Right(CONVERSATION_DETAILS))

        given(conversationRepository)
            .suspendFunction(conversationRepository::createGroupConversation)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(Either.Right(CONVERSATION))
        // when
        val result = getOrCreateOneToOneConversationUseCase.invoke(USER_ID)
        // then
        assertIs<CreateConversationResult.Success>(result)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::createGroupConversation)
            .with(anything(), anything(), anything())
            .wasNotInvoked()

        verify(conversationRepository)
            .suspendFunction(conversationRepository::getOneToOneConversationDetailsByUserId)
            .with(anything())
            .wasInvoked()
    }

    @Test
    fun givenConversationExist_whenCallingTheUseCase_ThenCreateAConversationAndReturn() = runTest {
        // given
        given(conversationRepository)
            .coroutine { getOneToOneConversationDetailsByUserId(USER_ID) }
            .then { Either.Left(StorageFailure.DataNotFound) }

        given(conversationRepository)
            .suspendFunction(conversationRepository::createGroupConversation)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(Either.Right(CONVERSATION))
        // when
        val result = getOrCreateOneToOneConversationUseCase.invoke(USER_ID)
        // then
        assertIs<CreateConversationResult.Success>(result)

        verify(conversationRepository)
            .coroutine { createGroupConversation(usersList = MEMBER) }
            .wasInvoked()
    }

    private companion object {
        val USER_ID = UserId(value = "userId", domain = "domainId")
        val MEMBER = listOf(USER_ID)
        val CONVERSATION_ID = ConversationId(value = "userId", domain = "domainId")
        val CONVERSATION = Conversation(
            id = CONVERSATION_ID,
            name = null,
            type = Conversation.Type.ONE_ON_ONE,
            teamId = null,
            ProtocolInfo.Proteus,
            MutedConversationStatus.AllAllowed,
            null,
            null,
            access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
            accessRole = listOf(Conversation.AccessRole.NON_TEAM_MEMBER, Conversation.AccessRole.GUEST)
        )
        val OTHER_USER = OtherUser(
            id =
            USER_ID,
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 0,
            teamId = null,
            previewPicture = null,
            completePicture = null,
            availabilityStatus = UserAvailabilityStatus.NONE,
            userType = UserType.EXTERNAL,
            connectionStatus = ConnectionState.NOT_CONNECTED
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
