package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.CallRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.CallRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cache
import kotlinx.coroutines.flow.cancel
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class EndCallOnConversationChangeUseCaseTest {

    @Test
    fun givenAnEstablishedCall_whenConversationIsDeleted_thenEndTheCurrentCall() = runTest {
        val (arrangement, endCallOnConversationChange) = arrange {
            withObserveConversationDetailsByIdReturning(Either.Left(StorageFailure.DataNotFound))
        }

        endCallOnConversationChange()

        verify(arrangement.endCall)
            .suspendFunction(arrangement.endCall::invoke)
            .with(eq(conversationId))
            .wasInvoked(once)
    }

    @Test
    fun givenAnEstablishedCall_whenUserIsRemovedFromConversation_thenEndTheCurrentCall() = runTest {
        val (arrangement, endCallOnConversationChange) = arrange {
            withObserveConversationDetailsByIdReturning(Either.Right(groupConversationDetail))
        }

        endCallOnConversationChange()

        verify(arrangement.endCall)
            .suspendFunction(arrangement.endCall::invoke)
            .with(eq(conversationId))
            .wasInvoked(once)
    }

    @Test
    fun givenAnEstablishedCall_whenUserDeletesHisAccount_thenEndTheCurrentCall() = runTest {
        val (arrangement, endCallOnConversationChange) = arrange {
            withObserveConversationDetailsByIdReturning(Either.Right(oneOnOneConversationDetail))
        }

        endCallOnConversationChange()

        verify(arrangement.endCall)
            .suspendFunction(arrangement.endCall::invoke)
            .with(eq(conversationId))
            .wasInvoked(once)
    }

    @Test
    fun givenAnEstablishedCall_whenConversationProteusDegraded_thenEndTheCurrentCall() = runTest {
        val flow = MutableSharedFlow<Either<StorageFailure, ConversationDetails>>()
        val (arrangement, endCallOnConversationChange) = arrange {
            withObserveConversationDetailsByIdReturningFlow(flow)
        }

        endCallOnConversationChange()
        flow.emit(Either.Right(oneOnOneConversationDetail.copy(conversation = conversation.copy(proteusVerificationStatus = Conversation.VerificationStatus.VERIFIED))))

        advanceUntilIdle()
        verify(arrangement.endCall)
            .suspendFunction(arrangement.endCall::invoke)
            .with(eq(conversationId))
            .wasNotInvoked()
        flow.emit(Either.Right(oneOnOneConversationDetail.copy(conversation = conversation.copy(proteusVerificationStatus = Conversation.VerificationStatus.DEGRADED))))

        advanceUntilIdle()
        verify(arrangement.endCall)
            .suspendFunction(arrangement.endCall::invoke)
            .with(eq(conversationId))
            .wasInvoked(once)
        cancel()
    }

    private class Arrangement(private val block: Arrangement.() -> Unit) :
        CallRepositoryArrangement by CallRepositoryArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl() {

        @Mock
        val endCall = mock(classOf<EndCallUseCase>())

        @Mock
        val endCallDialogManager = mock(classOf<EndCallDialogManager>())

        init {
            given(endCall)
                .suspendFunction(endCall::invoke)
                .whenInvokedWith(eq(conversationId))
                .thenDoNothing()
            given(endCallDialogManager)
                .suspendFunction(endCallDialogManager::scheduleEndCallDialogEvent)
                .whenInvokedWith(eq(conversationId))
                .thenDoNothing()

            withEstablishedCallsFlow(listOf(call))
        }

        fun arrange() = run {
            block()
            this@Arrangement to EndCallOnConversationChangeUseCaseImpl(
                callRepository = callRepository,
                conversationRepository = conversationRepository,
                endCallUseCase = endCall,
                dialogManager = endCallDialogManager
            )
        }
    }

    companion object {
        private fun arrange(configuration: Arrangement.() -> Unit) = Arrangement(configuration).arrange()

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
            protocol = Conversation.ProtocolInfo.Proteus,
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
            userMessageTimer = null,
            archived = false,
            archivedDateTime = null,
            mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED
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
            defederated = false,
            isProteusVerified = false,
            supportedProtocols = setOf(SupportedProtocol.PROTEUS)
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
