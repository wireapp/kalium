package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class EndCallOnConversationChangeUseCaseTest {

    @Mock
    private val callRepository = mock(classOf<CallRepository>())

    @Mock
    private val conversationRepository = mock(classOf<ConversationRepository>())

    @Mock
    private val endCall = mock(classOf<EndCallUseCase>())

    private lateinit var endCallOnConversationChange: EndCallOnConversationChangeUseCase

    @BeforeTest
    fun setup() {
        endCallOnConversationChange = EndCallOnConversationChangeUseCaseImpl(
            callRepository = callRepository,
            conversationRepository = conversationRepository,
            endCallUseCase = endCall
        )

        given(callRepository)
            .suspendFunction(callRepository::establishedCallsFlow)
            .whenInvoked()
            .thenReturn(flowOf(listOf(call)))

        given(endCall)
            .suspendFunction(endCall::invoke)
            .whenInvokedWith(eq(conversationId))
            .thenDoNothing()
    }

    @Test
    fun givenAnEstablishedCall_whenConversationIsDeleted_thenEndTheCurrentCall() = runTest {

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(eq(conversationId))
            .then {
                flowOf(Either.Left(StorageFailure.DataNotFound))
            }

        endCallOnConversationChange()

        verify(endCall)
            .suspendFunction(endCall::invoke)
            .with(eq(conversationId))
            .wasInvoked(once)
    }

    @Test
    fun givenAnEstablishedCall_whenUserIsRemovedFromConversation_thenEndTheCurrentCall() = runTest {

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(eq(conversationId))
            .then {
                flowOf(Either.Right(groupConversationDetail))
            }

        endCallOnConversationChange()

        verify(endCall)
            .suspendFunction(endCall::invoke)
            .with(eq(conversationId))
            .wasInvoked(once)
    }

    @Test
    fun givenAnEstablishedCall_whenUserDeletesHisAccount_thenEndTheCurrentCall() = runTest {
        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(eq(conversationId))
            .then {
                flowOf(Either.Right(oneOnOneConversationDetail))
            }

        endCallOnConversationChange()

        verify(endCall)
            .suspendFunction(endCall::invoke)
            .with(eq(conversationId))
            .wasInvoked(once)
    }

    companion object {
        val conversationId = ConversationId("conversationId", "domainId")
        private val call = Call(
            conversationId = conversationId,
            status = CallStatus.ESTABLISHED,
            callerId = "called-id",
            isMuted = false,
            isCameraOn = false,
            isCbrEnabled = false,
            conversationName = null,
            conversationType = Conversation.Type.GROUP,
            callerName = null,
            callerTeamName = null,
            establishedTime = null
        )

        val conversation = Conversation(
            id = conversationId,
            name = "Conv Name",
            type = Conversation.Type.ONE_ON_ONE,
            teamId = TeamId("team_id"),
            protocol = Conversation.ProtocolInfo.Proteus(Conversation.VerificationStatus.NOT_VERIFIED),
            mutedStatus = MutedConversationStatus.AllAllowed,
            removedBy = null,
            lastNotificationDate = null,
            lastModifiedDate = null,
            access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
            accessRole = Conversation.defaultGroupAccessRoles.toMutableList().apply { add(Conversation.AccessRole.GUEST) },
            lastReadDate = "2022-04-04T16:11:28.388Z",
            creatorId = null,
            receiptMode = Conversation.ReceiptMode.ENABLED,
            messageTimer = null,
            userMessageTimer = null
        )

        val otherUser = OtherUser(
            UserId(value = "otherValue", "domain"),
            name = "otherUsername",
            handle = "otherHandle",
            email = "otherEmail",
            phone = "otherPhone",
            accentId = 0,
            teamId = TeamId("otherTeamId"),
            connectionStatus = ConnectionState.ACCEPTED,
            previewPicture = UserAssetId("value", "domain"),
            completePicture = UserAssetId("value", "domain"),
            availabilityStatus = UserAvailabilityStatus.AVAILABLE,
            userType = UserType.INTERNAL,
            botService = null,
            deleted = true,
            defederated = false
        )

        private val groupConversationDetail = ConversationDetails.Group(
            conversation = conversation,
            legalHoldStatus = LegalHoldStatus.ENABLED,
            hasOngoingCall = true,
            unreadEventCount = mapOf(),
            lastMessage = null,
            isSelfUserMember = false,
            isSelfUserCreator = false,
            selfRole = null
        )

        private val oneOnOneConversationDetail = ConversationDetails.OneOne(
            conversation = conversation,
            legalHoldStatus = LegalHoldStatus.ENABLED,
            otherUser = otherUser,
            unreadEventCount = mapOf(),
            lastMessage = null,
            userType = UserType.ADMIN
        )
    }
}
