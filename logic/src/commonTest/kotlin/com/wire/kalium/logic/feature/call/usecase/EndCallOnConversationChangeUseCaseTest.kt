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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.CallRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.CallRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import io.mockative.Mock
import io.mockative.eq
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class EndCallOnConversationChangeUseCaseTest {

    @Test
    fun givenAnEstablishedCall_whenConversationIsDeleted_thenEndTheCurrentCall() = runTest {
        val (arrangement, endCallOnConversationChange) = arrange {
            withObserveConversationDetailsByIdReturning(Either.Left(StorageFailure.DataNotFound))
        }

        endCallOnConversationChange()

        coVerify {
            arrangement.endCall.invoke(eq(conversationId))
        }.wasInvoked(once)
    }

    @Test
    fun givenAnEstablishedCall_whenUserIsRemovedFromConversation_thenEndTheCurrentCall() = runTest {
        val (arrangement, endCallOnConversationChange) = arrange {
            withObserveConversationDetailsByIdReturning(Either.Right(groupConversationDetail))
        }

        endCallOnConversationChange()

        coVerify {
            arrangement.endCall.invoke(eq(conversationId))
        }.wasInvoked(once)
    }

    @Test
    fun givenAnEstablishedCall_whenUserDeletesHisAccount_thenEndTheCurrentCall() = runTest {
        val (arrangement, endCallOnConversationChange) = arrange {
            withObserveConversationDetailsByIdReturning(Either.Right(oneOnOneConversationDetail))
        }

        endCallOnConversationChange()

        coVerify {
            arrangement.endCall.invoke(eq(conversationId))
        }.wasInvoked(once)
    }

    @Test
    fun givenAnEstablishedCall_whenConversationProteusDegraded_thenEndTheCurrentCall() = runTest {
        val verifiedConversation = oneOnOneConversationDetail.copy(
            conversation = conversation.copy(proteusVerificationStatus = Conversation.VerificationStatus.VERIFIED),
            otherUser = otherUser.copy(deleted = false)
        )
        val value0 = Either.Right(verifiedConversation)
        val value1 =
            Either.Right(
                verifiedConversation.copy(
                    conversation = conversation.copy(proteusVerificationStatus = Conversation.VerificationStatus.DEGRADED)
                )
            )
        val (arrangement, endCallOnConversationChange) = arrange {
            withObserveConversationDetailsByIdReturning(value0, value1)
        }

        endCallOnConversationChange()

        coVerify {
            arrangement.endCall.invoke(eq(conversationId))
        }.wasInvoked(once)
    }

    @Test
    fun givenAnEstablishedCall_whenConversationMLSDegraded_thenEndTheCurrentCall() = runTest {
        val verifiedConversation = oneOnOneConversationDetail.copy(
            conversation = conversation.copy(mlsVerificationStatus = Conversation.VerificationStatus.VERIFIED),
            otherUser = otherUser.copy(deleted = false)
        )
        val value0 = Either.Right(verifiedConversation)
        val value1 =
            Either.Right(
                verifiedConversation.copy(
                    conversation = conversation.copy(mlsVerificationStatus = Conversation.VerificationStatus.DEGRADED)
                )
            )
        val (arrangement, endCallOnConversationChange) = arrange {
            withObserveConversationDetailsByIdReturning(value0, value1)
        }

        endCallOnConversationChange()

        coVerify {
            arrangement.endCall.invoke(eq(conversationId))
        }.wasInvoked(once)
    }

    @Test
    fun givenAnEstablishedCallInVerifiedConversationAndUserIsOkay_thenCurrentCallIsNotEnded() = runTest {
        val verifiedConversation = oneOnOneConversationDetail.copy(
            conversation = conversation.copy(proteusVerificationStatus = Conversation.VerificationStatus.VERIFIED),
            otherUser = otherUser.copy(deleted = false)
        )
        val (arrangement, endCallOnConversationChange) = arrange {
            withObserveConversationDetailsByIdReturning(Either.Right(verifiedConversation))
        }

        endCallOnConversationChange()

        coVerify {
            arrangement.endCall.invoke(eq(conversationId))
        }.wasNotInvoked()
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        CallRepositoryArrangement by CallRepositoryArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl() {

        @Mock
        val endCall = mock(EndCallUseCase::class)

        @Mock
        val endCallDialogManager = mock(EndCallResultListener::class)

        suspend fun arrange() = run {
            block()
            this@Arrangement to EndCallOnConversationChangeUseCaseImpl(
                callRepository = callRepository,
                conversationRepository = conversationRepository,
                endCallUseCase = endCall,
                endCallListener = endCallDialogManager
            )
        }.also {
            coEvery {
                endCall.invoke(eq(conversationId))
            }.returns(Unit)
            coEvery {
                endCallDialogManager.onCallEndedBecauseOfVerificationDegraded()
            }.returns(Unit)

            withEstablishedCallsFlow(listOf(call))
        }
    }

    companion object {
        private suspend fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        val conversationId = ConversationId("conversationId", "domainId")
        private val call = Call(
            conversationId = conversationId,
            status = CallStatus.ESTABLISHED,
            callerId = UserId("called-id", "domain"),
            isMuted = false,
            isCameraOn = false,
            isCbrEnabled = false,
            conversationName = null,
            conversationType = Conversation.Type.Group.Regular,
            callerName = null,
            callerTeamName = null,
            establishedTime = null
        )

        val conversation = Conversation(
            id = conversationId,
            name = "Conv Name",
            type = Conversation.Type.OneOnOne,
            teamId = TeamId("team_id"),
            protocol = Conversation.ProtocolInfo.Proteus,
            mutedStatus = MutedConversationStatus.AllAllowed,
            removedBy = null,
            lastNotificationDate = null,
            lastModifiedDate = null,
            access = listOf(Conversation.Access.CODE, Conversation.Access.INVITE),
            accessRole = Conversation.defaultGroupAccessRoles.toMutableList().apply { add(Conversation.AccessRole.GUEST) },
            lastReadDate = Instant.parse("2022-04-04T16:11:28.388Z"),
            creatorId = null,
            receiptMode = Conversation.ReceiptMode.ENABLED,
            messageTimer = null,
            userMessageTimer = null,
            archived = false,
            archivedDateTime = null,
            mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
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

        private val groupConversationDetail = ConversationDetails.Group.Regular(
            conversation = conversation,
            hasOngoingCall = true,
            isSelfUserMember = false,
            selfRole = null
        )

        private val oneOnOneConversationDetail = ConversationDetails.OneOne(
            conversation = conversation,
            otherUser = otherUser,
            userType = UserType.ADMIN
        )
    }
}
