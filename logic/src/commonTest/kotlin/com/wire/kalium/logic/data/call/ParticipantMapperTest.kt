package com.wire.kalium.logic.data.call

import com.wire.kalium.calling.VideoStateCalling
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.call.mapper.ParticipantMapperImpl
import com.wire.kalium.logic.data.id.QualifiedID
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ParticipantMapperTest {

    @Mock
    private val videoStateChecker = mock(classOf<VideoStateChecker>())

    @Mock
    private val callMapper = mock(classOf<CallMapper>())

    private val participantMapperImpl = ParticipantMapperImpl(videoStateChecker, callMapper)

    @BeforeTest
    fun setUp() {
        given(callMapper).invocation { callMapper.fromIntToCallingVideoState(0) }
            .then { VideoStateCalling.STOPPED }
        given(videoStateChecker).invocation { videoStateChecker.isCameraOn(VideoStateCalling.STOPPED) }
            .then { false }
        given(videoStateChecker).invocation { videoStateChecker.isSharingScreen(VideoStateCalling.STOPPED) }
            .then { false }
    }

    @Test
    fun whenMappingToParticipant_withCallMember_thenReturnParticipant() = runTest {
        val participantMap = participantMapperImpl.fromCallMemberToParticipant(
            member = DUMMY_CALL_MEMBER
        )

        val expectedParticipant = Participant(
            id = QualifiedID(
                value = "dummyId",
                domain = "dummyDomain"
            ),
            clientId = "dummyClientId",
            isMuted = false,
            isCameraOn = false,
            isSharingScreen = false
        )

        assertEquals(expectedParticipant, participantMap)
    }

    @Test
    fun whenMappingToCallClient_withCallMember_thenReturnCallMember() = runTest {
        val callClientMap = participantMapperImpl.fromCallMemberToCallClient(
            member = DUMMY_CALL_MEMBER
        )

        val expectedCallClient = CallClient(
            userId = "dummyId@dummyDomain",
            clientId = "dummyClientId"
        )

        assertEquals(expectedCallClient, callClientMap)
    }

    companion object {
        private val DUMMY_CALL_MEMBER = CallMember(
            userId = "dummyId@dummyDomain",
            clientId = "dummyClientId",
            aestab = 0,
            vrecv = 0,
            isMuted = 0
        )
    }
}
