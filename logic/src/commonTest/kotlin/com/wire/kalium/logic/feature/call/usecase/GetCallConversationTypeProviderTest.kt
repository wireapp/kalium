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

import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.ConversationTypeForCall
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
<<<<<<< HEAD
import io.mockative.coEvery
import io.mockative.eq
import io.mockative.every
=======
import io.mockative.eq
import io.mockative.given
>>>>>>> f712fc2340 (feat: Use sft for one to one calls when needed cherry pick 4.6  (WPB-7153) (#2907))
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class GetCallConversationTypeProviderTest {

    @Test
    fun givenShouldUseSFTForOneOnOneCallsAndMLSEnabled_whenRunningUseCase_thenReturnConferenceMls() =
        runTest {
            val conversationId = TestConversation.ID
            val groupId = GroupID("groupid")

            val (arrangement, getCallConversationType) = Arrangement()
                .withGetConversationTypeByIdSuccess(conversationId, Conversation.Type.ONE_ON_ONE)
                .withGetConversationProtocolInfoSuccess(
                    conversationId,
                    Conversation.ProtocolInfo.MLS(
                        groupId,
                        Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                        1UL,
                        Clock.System.now(),
                        CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                    )
                )
                .withMlsConferenceCallMapping()
                .withShouldUseSFTForOneOnOneCalls()
                .withMLSEnabled()
                .arrange()

            val result = getCallConversationType.invoke(conversationId)

            assertEquals(ConversationTypeCalling.ConferenceMls, result)
        }

    @Test
    fun givenShouldUseSFTForOneOnOneCallsAndMLSDisabled_whenRunningUseCase_thenReturnConference() =
        runTest {
            val conversationId = TestConversation.ID

            val (_, getCallConversationType) = Arrangement()
                .withGetConversationTypeByIdSuccess(conversationId, Conversation.Type.GROUP)
                .withGetConversationProtocolInfoSuccess(
                    conversationId,
                    Conversation.ProtocolInfo.Proteus
                )
                .withConferenceCallMapping()
                .withShouldUseSFTForOneOnOneCalls()
                .withMLSDisabled()
                .arrange()

            val result = getCallConversationType(conversationId)

            assertEquals(ConversationTypeCalling.Conference, result)
        }

    @Test
    fun givenShouldNotUseSFTForOneOnOneCallsAndOneOnOneConversation_whenRunningUseCase_thenReturnOneOnOneType() =
        runTest {
            val conversationId = TestConversation.ID

            val (_, getCallConversationType) = Arrangement()
                .withShouldNotUseSFTForOneOnOneCalls()
                .withGetConversationTypeByIdSuccess(conversationId, Conversation.Type.ONE_ON_ONE)
                .withGetConversationProtocolInfoSuccess(
                    conversationId,
                    Conversation.ProtocolInfo.Proteus
                )
                .withOneOnOneCallMapping()
                .arrange()

            val result = getCallConversationType.invoke(conversationId)

            assertEquals(ConversationTypeCalling.OneOnOne, result)
        }

    @Test
    fun givenUserConfigRepositoryReturnsFailure_whenRunningUseCase_thenReturnConversationType() =
        runTest {
            val conversationId = TestConversation.ID

            val (_, getCallConversationType) = Arrangement()
                .withShouldUseSFTForOneOnOneCallsFailure()
                .withGetConversationTypeByIdSuccess(conversationId, Conversation.Type.GROUP)
                .withGetConversationProtocolInfoSuccess(
                    conversationId,
                    Conversation.ProtocolInfo.Proteus
                )
                .withConferenceCallMapping()
                .arrange()

            val result = getCallConversationType.invoke(conversationId)

            assertEquals(ConversationTypeCalling.Conference, result)
        }

    @Test
    fun givenShouldNotUseSFTAndConversationRepositoryFailure_whenRunningUseCase_thenReturnUnknown() =
        runTest {
            val conversationId = TestConversation.ID

            val (_, getCallConversationType) = Arrangement()
                .withShouldNotUseSFTForOneOnOneCalls()
                .withGetConversationTypeByIdFailure(conversationId)
                .withUnknownCallMapping()
                .arrange()

            val result = getCallConversationType.invoke(conversationId)

            assertEquals(ConversationTypeCalling.Unknown, result)
        }

    private class Arrangement {

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val callMapper = mock(CallMapper::class)

        private val getCallConversationType = GetCallConversationTypeProviderImpl(
            userConfigRepository = userConfigRepository,
            conversationRepository = conversationRepository,
            callMapper = callMapper
        )

        fun arrange() = this to getCallConversationType

        fun withMLSEnabled() = apply {
<<<<<<< HEAD
            every {
                userConfigRepository.isMLSEnabled()
            }.returns(Either.Right(true))
        }

        fun withMLSDisabled() = apply {
            every {
                userConfigRepository.isMLSEnabled()
            }.returns(Either.Right(false))
        }

        fun withShouldUseSFTForOneOnOneCalls() = apply {
            every {
                userConfigRepository.shouldUseSFTForOneOnOneCalls()
            }.returns(Either.Right(true))
        }

        fun withShouldNotUseSFTForOneOnOneCalls() = apply {
            every {
                userConfigRepository.shouldUseSFTForOneOnOneCalls()
            }.returns(Either.Right(false))
        }

        fun withShouldUseSFTForOneOnOneCallsFailure() = apply {
            every {
                userConfigRepository.shouldUseSFTForOneOnOneCalls()
            }.returns(Either.Left(StorageFailure.DataNotFound))
=======
            given(userConfigRepository)
                .function(userConfigRepository::isMLSEnabled)
                .whenInvoked()
                .thenReturn(Either.Right(true))
        }

        fun withMLSDisabled() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::isMLSEnabled)
                .whenInvoked()
                .thenReturn(Either.Right(false))
        }

        fun withShouldUseSFTForOneOnOneCalls() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::shouldUseSFTForOneOnOneCalls)
                .whenInvoked()
                .thenReturn(Either.Right(true))
        }

        fun withShouldNotUseSFTForOneOnOneCalls() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::shouldUseSFTForOneOnOneCalls)
                .whenInvoked()
                .thenReturn(Either.Right(false))
        }

        fun withShouldUseSFTForOneOnOneCallsFailure() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::shouldUseSFTForOneOnOneCalls)
                .whenInvoked()
                .thenReturn(Either.Left(StorageFailure.DataNotFound))
>>>>>>> f712fc2340 (feat: Use sft for one to one calls when needed cherry pick 4.6  (WPB-7153) (#2907))
        }

        suspend fun withGetConversationTypeByIdSuccess(
            conversationId: ConversationId,
            result: Conversation.Type
        ) = apply {
<<<<<<< HEAD
            coEvery {
                conversationRepository.getConversationTypeById(eq(conversationId))
            }.returns(Either.Right(result))
        }

        suspend fun withGetConversationTypeByIdFailure(conversationId: ConversationId) = apply {
            coEvery {
                conversationRepository.getConversationTypeById(eq(conversationId))
            }.returns(Either.Left(StorageFailure.DataNotFound))
=======
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationTypeById)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(result))
        }

        suspend fun withGetConversationTypeByIdFailure(conversationId: ConversationId) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationTypeById)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(StorageFailure.DataNotFound))
>>>>>>> f712fc2340 (feat: Use sft for one to one calls when needed cherry pick 4.6  (WPB-7153) (#2907))
        }

        suspend fun withGetConversationProtocolInfoSuccess(
            conversationId: ConversationId,
            protocolResult: Conversation.ProtocolInfo
        ) = apply {
<<<<<<< HEAD
            coEvery {
                conversationRepository.getConversationProtocolInfo(eq(conversationId))
            }.returns(Either.Right(protocolResult))
        }

        fun withOneOnOneCallMapping() = apply {
            every {
                callMapper.fromConversationTypeToConversationTypeForCall(any(), any())
            }.returns(ConversationTypeForCall.OneOnOne)

            every {
                callMapper.toConversationTypeCalling(any())
            }.returns(ConversationTypeCalling.OneOnOne)
        }

        fun withConferenceCallMapping() = apply {
            every {
                callMapper.fromConversationTypeToConversationTypeForCall(any(), any())
            }.returns(ConversationTypeForCall.Conference)

            every {
                callMapper.toConversationTypeCalling(any())
            }.returns(ConversationTypeCalling.Conference)
        }

        fun withMlsConferenceCallMapping() = apply {
            every {
                callMapper.fromConversationTypeToConversationTypeForCall(any(), any())
            }.returns(ConversationTypeForCall.ConferenceMls)

            every {
                callMapper.toConversationTypeCalling(any())
            }.returns(ConversationTypeCalling.ConferenceMls)
        }

        fun withUnknownCallMapping() = apply {
            every {
                callMapper.toConversationTypeCalling(eq(ConversationTypeForCall.Unknown))
            }.returns(ConversationTypeCalling.Unknown)
=======
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationProtocolInfo)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(protocolResult))
        }

        fun withOneOnOneCallMapping() = apply {
            given(callMapper)
                .function(callMapper::fromConversationTypeToConversationTypeForCall)
                .whenInvokedWith(any(), any())
                .thenReturn(ConversationTypeForCall.OneOnOne)
            given(callMapper)
                .function(callMapper::toConversationTypeCalling)
                .whenInvokedWith(any())
                .thenReturn(ConversationTypeCalling.OneOnOne)
        }

        fun withConferenceCallMapping() = apply {
            given(callMapper)
                .function(callMapper::fromConversationTypeToConversationTypeForCall)
                .whenInvokedWith(any(), any())
                .thenReturn(ConversationTypeForCall.Conference)

            given(callMapper)
                .function(callMapper::toConversationTypeCalling)
                .whenInvokedWith(any())
                .thenReturn(ConversationTypeCalling.Conference)
        }

        fun withMlsConferenceCallMapping() = apply {
            given(callMapper)
                .function(callMapper::fromConversationTypeToConversationTypeForCall)
                .whenInvokedWith(any(), any())
                .thenReturn(ConversationTypeForCall.ConferenceMls)

            given(callMapper)
                .function(callMapper::toConversationTypeCalling)
                .whenInvokedWith(any())
                .thenReturn(ConversationTypeCalling.ConferenceMls)
        }

        fun withUnknownCallMapping() = apply {
            given(callMapper)
                .function(callMapper::toConversationTypeCalling)
                .whenInvokedWith(eq(ConversationTypeForCall.Unknown))
                .then {
                    ConversationTypeCalling.Unknown
                }
>>>>>>> f712fc2340 (feat: Use sft for one to one calls when needed cherry pick 4.6  (WPB-7153) (#2907))
        }
    }
}
