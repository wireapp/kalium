package com.wire.kalium.logic.data.call

import com.wire.kalium.calling.CallTypeCalling
import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.calling.VideoStateCalling
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.call.CallEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CallMapperTest {

    private lateinit var callMapper: CallMapper

    @BeforeTest
    fun setUp() {
        callMapper = CallMapper()
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
        val oneOnOneMap = callMapper.toConversationTypeCalling(conversationType = ConversationType.OneOnOne)
        val conferenceMap = callMapper.toConversationTypeCalling(conversationType = ConversationType.Conference)
        val unknown = callMapper.toConversationTypeCalling(conversationType = ConversationType.Unknown)

        assertEquals(ConversationTypeCalling.OneOnOne, oneOnOneMap)
        assertEquals(ConversationTypeCalling.Conference, conferenceMap)
        assertEquals(ConversationTypeCalling.Unknown, unknown)
    }

    @Test
    fun givenVideoStates_whenMappingWithToVideoStateCalling_thenReturnsTheCorrespondentValues() = runTest {
        val stopped = callMapper.toVideoStateCalling(videoState = VideoState.STOPPED)
        val started = callMapper.toVideoStateCalling(videoState = VideoState.STARTED)
        val badConnection = callMapper.toVideoStateCalling(videoState = VideoState.BAD_CONNECTION)
        val paused = callMapper.toVideoStateCalling(videoState = VideoState.PAUSED)
        val screenshare = callMapper.toVideoStateCalling(videoState = VideoState.SCREENSHARE)

        assertEquals(VideoStateCalling.STOPPED, stopped)
        assertEquals(VideoStateCalling.STARTED, started)
        assertEquals(VideoStateCalling.BAD_CONNECTION, badConnection)
        assertEquals(VideoStateCalling.PAUSED, paused)
        assertEquals(VideoStateCalling.SCREENSHARE, screenshare)
    }

    @Test
    fun whenMappingToParticipant_withCallMember_thenReturnParticipant() = runTest {
        val participantMap = callMapper.participantMapper.fromCallMemberToParticipant(
            member = DUMMY_CALL_MEMBER
        )

        val expectedParticipant = Participant(
            id = QualifiedID(
                value = "dummyId",
                domain = "dummyDomain"
            ),
            clientId = "dummyClientId",
            isMuted = false
        )

        assertEquals(expectedParticipant, participantMap)
    }

    @Test
    fun whenMappingToCallClient_withCallMember_thenReturnCallMember() = runTest {
        val callClientMap = callMapper.participantMapper.fromCallMemberToCallClient(
            member = DUMMY_CALL_MEMBER
        )

        val expectedCallClient = CallClient(
            userId = "dummyId@dummyDomain",
            clientId = "dummyClientId"
        )

        assertEquals(expectedCallClient, callClientMap)
    }

    @Test
    fun given0AsAConversationTypeInputValue_whenMappingToConversationType_ThenReturnOneOnOneType() {
        val expected = ConversationType.OneOnOne

        val actual = callMapper.fromIntToConversationType(0)

        assertEquals(expected, actual)
    }

    @Test
    fun given2AsAConversationTypeInputValue_whenMappingToConversationType_ThenReturnConferenceType() {
        val expected = ConversationType.Conference

        val actual = callMapper.fromIntToConversationType(2)

        assertEquals(expected, actual)
    }

    @Test
    fun givenADifferentAConversationTypeInputValue_whenMappingToConversationType_ThenReturnConferenceType() {
        val expected = ConversationType.Unknown

        val actual = callMapper.fromIntToConversationType(4)

        assertEquals(expected, actual)
    }

    @Test
    fun givenCallActiveSpeakers_whenMappingToParticipantsActiveSpeaker_thenReturnParticipantsActiveSpeaker() = runTest {
        val dummyParticipantWithDifferentClientId = DUMMY_PARTICIPANT.copy(
            clientId = "anotherClientId"
        )

        val callActiveSpeakerMap = callMapper.activeSpeakerMapper.mapParticipantsActiveSpeaker(
            participants = listOf(
                DUMMY_PARTICIPANT,
                dummyParticipantWithDifferentClientId
            ),
            activeSpeakers = CallActiveSpeakers(
                activeSpeakers = listOf(DUMMY_CALL_ACTIVE_SPEAKER)
            )
        )

        val expectedParticipantsActiveSpeaker = listOf(
            DUMMY_PARTICIPANT.copy(
                isSpeaking = true
            ),
            dummyParticipantWithDifferentClientId
        )

        assertEquals(expectedParticipantsActiveSpeaker, callActiveSpeakerMap)
    }

    @Test
    fun givenCallData_whenMappingToCallEntity_thenReturnCallEntity() = runTest {
        // given
        val conversationId = ConversationId(
            value = "convId",
            domain = "domainId"
        )
        val id = "abcd-1234"
        val callerId = UserId(
            value = "callerValue",
            domain = "callerDomain"
        )
        val expectedCallEntity = CallEntity(
            conversationId = QualifiedIDEntity(
                value = conversationId.value,
                domain = conversationId.domain
            ),
            id = id,
            status = CallEntity.Status.ESTABLISHED,
            callerId = callerId.toString(),
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )

        // when
        val result = callMapper.toCallEntity(
            conversationId = conversationId,
            id = id,
            status = CallStatus.ESTABLISHED,
            conversationType = Conversation.Type.ONE_ON_ONE,
            callerId = callerId
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
        val conversationId = ConversationId(
            value = "convId",
            domain = "domainId"
        )
        val id = "abcd-1234"
        val callerId = "callerValue@callerDomain"
        val callEntity = CallEntity(
            conversationId = QualifiedIDEntity(
                value = conversationId.value,
                domain = conversationId.domain
            ),
            id = id,
            status = CallEntity.Status.ESTABLISHED,
            callerId = callerId,
            conversationType = ConversationEntity.Type.ONE_ON_ONE
        )
        val callMetadata = CallMetaData(
            isMuted = true,
            isCameraOn = false,
            conversationName = "conv name",
            conversationType = Conversation.Type.ONE_ON_ONE,
            callerName = "caller name",
            callerTeamName = "caller team name",
            establishedTime = null
        )

        val expectedCall = Call(
            conversationId = conversationId,
            status = CallStatus.ESTABLISHED,
            isMuted = true,
            isCameraOn = false,
            callerId = callerId,
            conversationName = "conv name",
            conversationType = Conversation.Type.ONE_ON_ONE,
            callerName = "caller name",
            callerTeamName = "caller team name",
            establishedTime = null,
            participants = emptyList(),
            maxParticipants = 0
        )

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
    fun givenAConversationId_whenMappingToQualifiedIDEntity_thenReturnCorrectQualifiedIDEntity() = runTest {
        val result = callMapper.fromConversationIdToQualifiedIDEntity(
            conversationId = ConversationId(
                value = "convId",
                domain = "domainId"
            )
        )

        assertEquals(
            QualifiedIDEntity(
                value = "convId",
                domain = "domainId"
            ),
            result
        )
    }

    private companion object {
        private val DUMMY_CALL_MEMBER = CallMember(
            userId = "dummyId@dummyDomain",
            clientId = "dummyClientId",
            aestab = 0,
            vrecv = 0,
            isMuted = 0
        )
        private val DUMMY_CALL_ACTIVE_SPEAKER = CallActiveSpeaker(
            userId = "dummyId@dummyDomain",
            clientId = "dummyClientId",
            audioLevel = 1,
            audioLevelNow = 1
        )
        private val DUMMY_PARTICIPANT = Participant(
            id = QualifiedID(
                value = "dummyId",
                domain = "dummyDomain"
            ),
            clientId = "dummyClientId",
            isMuted = false,
            isSpeaking = false
        )
    }

}
