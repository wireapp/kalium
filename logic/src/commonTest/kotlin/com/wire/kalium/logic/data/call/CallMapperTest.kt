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

package com.wire.kalium.logic.data.call

import com.wire.kalium.calling.CallTypeCalling
import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.calling.VideoStateCalling
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.call.mapper.CallMapperImpl
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapperImpl
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.persistence.dao.call.CallEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CallMapperTest {

    private lateinit var qualifiedIdMapper: QualifiedIdMapper

    private lateinit var callMapper: CallMapper

    @BeforeTest
    fun setUp() {
        qualifiedIdMapper = QualifiedIdMapperImpl(selfUserId = TestCall.CALLER_ID)
        callMapper = CallMapperImpl(qualifiedIdMapper = qualifiedIdMapper)
    }

    @Test
    fun whenMappingToCallTypeCalling_withCallType_thenReturnCallTypeCalling() = runTest {
        val audioMap = callMapper.toCallTypeCalling(callType = CallType.AUDIO)
        val videoMap = callMapper.toCallTypeCalling(callType = CallType.VIDEO)

        assertEquals(CallTypeCalling.AUDIO, audioMap)
        assertEquals(CallTypeCalling.VIDEO, videoMap)
    }

    @Test
    fun whenMappingToConversationTypeCalling_withConversationType_thenReturnConversationTypeCalling() = runTest {
        val oneOnOneMap = callMapper.toConversationTypeCalling(conversationTypeForCall = ConversationTypeForCall.OneOnOne)
        val conferenceMap = callMapper.toConversationTypeCalling(conversationTypeForCall = ConversationTypeForCall.Conference)
        val unknown = callMapper.toConversationTypeCalling(conversationTypeForCall = ConversationTypeForCall.Unknown)

        assertEquals(ConversationTypeCalling.OneOnOne, oneOnOneMap)
        assertEquals(ConversationTypeCalling.Conference, conferenceMap)
        assertEquals(ConversationTypeCalling.Unknown, unknown)
    }

    @Test
    fun givenCallMapper_whenMappingToConversationType_thenReturnConversationType() = runTest {
        val oneOnOneMap = callMapper.toConversationType(conversationType = ConversationEntity.Type.ONE_ON_ONE)
        val conferenceMap = callMapper.toConversationType(conversationType = ConversationEntity.Type.GROUP)

        assertEquals(Conversation.Type.OneOnOne, oneOnOneMap)
        assertEquals(Conversation.Type.Group.Regular, conferenceMap)
    }

    @Test
    fun givenVideoStates_whenMappingWithToVideoStateCalling_thenReturnsTheCorrespondentValues() = runTest {
        val stopped = callMapper.toVideoStateCalling(videoState = VideoState.STOPPED)
        val started = callMapper.toVideoStateCalling(videoState = VideoState.STARTED)
        val badConnection = callMapper.toVideoStateCalling(videoState = VideoState.BAD_CONNECTION)
        val paused = callMapper.toVideoStateCalling(videoState = VideoState.PAUSED)
        val screenshare = callMapper.toVideoStateCalling(videoState = VideoState.SCREENSHARE)
        val unknown = callMapper.toVideoStateCalling(videoState = VideoState.UNKNOWN)

        assertEquals(VideoStateCalling.STOPPED, stopped)
        assertEquals(VideoStateCalling.STARTED, started)
        assertEquals(VideoStateCalling.BAD_CONNECTION, badConnection)
        assertEquals(VideoStateCalling.PAUSED, paused)
        assertEquals(VideoStateCalling.SCREENSHARE, screenshare)
        assertEquals(VideoStateCalling.UNKNOWN, unknown)
    }

    @Test
    fun given0AsAConversationTypeInputValue_whenMappingToConversationType_ThenReturnOneOnOneType() {
        val expected = ConversationTypeForCall.OneOnOne

        val actual = callMapper.fromIntToConversationType(0)

        assertEquals(expected, actual)
    }

    @Test
    fun given2AsAConversationTypeInputValue_whenMappingToConversationType_ThenReturnConferenceType() {
        val expected = ConversationTypeForCall.Conference

        val actual = callMapper.fromIntToConversationType(2)

        assertEquals(expected, actual)
    }

    @Test
    fun givenADifferentAConversationTypeInputValue_whenMappingToConversationType_ThenReturnConferenceType() {
        val expected = ConversationTypeForCall.Unknown

        val actual = callMapper.fromIntToConversationType(4)

        assertEquals(expected, actual)
    }

    @Test
    fun givenCallData_whenMappingToCallEntity_thenReturnCallEntity() = runTest {
        // given
        val expectedCallEntity = TestCall.oneOnOneEstablishedCallEntity()

        // when
        val result = callMapper.toCallEntity(
            conversationId = TestCall.CONVERSATION_ID,
            id = TestCall.DATABASE_ID,
            status = CallStatus.ESTABLISHED,
            conversationType = Conversation.Type.OneOnOne,
            callerId = TestCall.CALLER_ID,
            type = ConversationTypeForCall.OneOnOne
        )

        // then
        assertEquals(
            expectedCallEntity,
            result
        )
    }

    @Test
    fun givenCallEntityAndCallMetadata_whenMappingToCall_thenReturnCall() = runTest {
        // given
        val callEntity = TestCall.oneOnOneEstablishedCallEntity()
        val callMetadata = TestCall.oneOnOneCallMetadata()

        val expectedCall = TestCall.oneOnOneEstablishedCall()

        // when
        val result = callMapper.toCall(
            callEntity = callEntity,
            metadata = callMetadata
        )

        // then
        assertEquals(
            expectedCall,
            result
        )
    }

    @Test
    fun givenACallStatusSTARTED_whenMappingToCallEntityStatus_thenReturnCorrectCallEntityStatus() = runTest {
        val result = callMapper.toCallEntityStatus(callStatus = CallStatus.STARTED)

        assertEquals(
            CallEntity.Status.STARTED,
            result
        )
    }

    @Test
    fun givenACallStatusCLOSED_INTERNALLY_whenMappingToCallEntityStatus_thenReturnCorrectCallEntityStatus() = runTest {
        val result = callMapper.toCallEntityStatus(callStatus = CallStatus.CLOSED_INTERNALLY)

        assertEquals(
            CallEntity.Status.CLOSED_INTERNALLY,
            result
        )
    }

    @Test
    fun givenACallStatusINCOMING_whenMappingToCallEntityStatus_thenReturnCorrectCallEntityStatus() = runTest {
        val result = callMapper.toCallEntityStatus(callStatus = CallStatus.INCOMING)

        assertEquals(
            CallEntity.Status.INCOMING,
            result
        )
    }

    @Test
    fun givenACallStatusMISSED_whenMappingToCallEntityStatus_thenReturnCorrectCallEntityStatus() = runTest {
        val result = callMapper.toCallEntityStatus(callStatus = CallStatus.MISSED)

        assertEquals(
            CallEntity.Status.MISSED,
            result
        )
    }

    @Test
    fun givenACallStatusANSWERED_whenMappingToCallEntityStatus_thenReturnCorrectCallEntityStatus() = runTest {
        val result = callMapper.toCallEntityStatus(callStatus = CallStatus.ANSWERED)

        assertEquals(
            CallEntity.Status.ANSWERED,
            result
        )
    }

    @Test
    fun givenACallStatusESTABLISHED_whenMappingToCallEntityStatus_thenReturnCorrectCallEntityStatus() = runTest {
        val result = callMapper.toCallEntityStatus(callStatus = CallStatus.ESTABLISHED)

        assertEquals(
            CallEntity.Status.ESTABLISHED,
            result
        )
    }

    @Test
    fun givenACallStatusSTILL_ONGOING_whenMappingToCallEntityStatus_thenReturnCorrectCallEntityStatus() = runTest {
        val result = callMapper.toCallEntityStatus(callStatus = CallStatus.STILL_ONGOING)

        assertEquals(
            CallEntity.Status.STILL_ONGOING,
            result
        )
    }

    @Test
    fun givenACallStatusCLOSED_whenMappingToCallEntityStatus_thenReturnCorrectCallEntityStatus() = runTest {
        val result = callMapper.toCallEntityStatus(callStatus = CallStatus.CLOSED)

        assertEquals(
            CallEntity.Status.CLOSED,
            result
        )
    }

    @Test
    fun givenACallStatusREJECTED_whenMappingToCallEntityStatus_thenReturnCorrectCallEntityStatus() = runTest {
        val result = callMapper.toCallEntityStatus(callStatus = CallStatus.REJECTED)

        assertEquals(
            CallEntity.Status.REJECTED,
            result
        )
    }

    @Test
    fun givenAConversationId_whenMappingToQualifiedIDEntity_thenReturnCorrectQualifiedIDEntity() = runTest {
        val result = callMapper.fromConversationIdToQualifiedIDEntity(
            conversationId = TestCall.CONVERSATION_ID
        )

        assertEquals(
            TestCall.qualifiedIdEntity(),
            result
        )
    }

    @Test
    fun givenVideoStateInt_whenParsingToVideoState_thenReturnCorrespondingValues() {
        val videoStatedStoppedInt = 0
        val videoStatedStartedInt = 1
        val videoStatedBadConnectionInt = 2
        val videoStatedPausedInt = 3
        val videoStatedScreenShareInt = 4
        val videoStatedUnknownInt = 5

        val resultStopped = callMapper.fromIntToCallingVideoState(videoStatedStoppedInt)
        val resultStarted = callMapper.fromIntToCallingVideoState(videoStatedStartedInt)
        val resultBadConnection = callMapper.fromIntToCallingVideoState(videoStatedBadConnectionInt)
        val resultPaused = callMapper.fromIntToCallingVideoState(videoStatedPausedInt)
        val resultScreenShare = callMapper.fromIntToCallingVideoState(videoStatedScreenShareInt)
        val resultUnknown = callMapper.fromIntToCallingVideoState(videoStatedUnknownInt)

        assertEquals(VideoStateCalling.STOPPED, resultStopped)
        assertEquals(VideoStateCalling.STARTED, resultStarted)
        assertEquals(VideoStateCalling.BAD_CONNECTION, resultBadConnection)
        assertEquals(VideoStateCalling.PAUSED, resultPaused)
        assertEquals(VideoStateCalling.SCREENSHARE, resultScreenShare)
        assertEquals(VideoStateCalling.UNKNOWN, resultUnknown)
    }

    @Test
    fun givenACallClientList_whenMappingToMessageTarget_thenReturnCorrectMessageTargetClients() = runTest {
        // given
        val callClientList = CallClientList(
            clients = listOf(
                CallClient(
                    userId = TestCall.CALLER_ID.toString(),
                    clientId = TestCall.CLIENT_ID_1
                ),
                CallClient(
                    userId = TestCall.CALLER_ID.toString(),
                    clientId = TestCall.CLIENT_ID_2
                )
            )
        )
        val expectedMessageTarget = MessageTarget.Client(
            recipients = listOf(
                Recipient(
                    id = TestCall.CALLER_ID,
                    clients = listOf(
                        ClientId(TestCall.CLIENT_ID_1),
                        ClientId(TestCall.CLIENT_ID_2)
                    )
                )
            )
        )

        // when
        val result = callMapper.toClientMessageTarget(callClientList)

        // then
        assertEquals(
            expectedMessageTarget.recipients,
            result.recipients
        )
    }
}
