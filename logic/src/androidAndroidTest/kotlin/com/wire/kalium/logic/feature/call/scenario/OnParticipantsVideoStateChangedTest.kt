package com.wire.kalium.logic.feature.call.scenario

import com.wire.kalium.calling.VideoStateCalling
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlin.test.BeforeTest
import kotlin.test.Test

class OnParticipantsVideoStateChangedTest {

    @Mock
    private val callRepository = mock(classOf<CallRepository>())

    @Mock
    private val callMapper = mock(classOf<CallMapper>())

    @Mock
    private val videoStateChecker = mock(classOf<VideoStateChecker>())

    @Mock
    private val qualifiedIdMapper = mock(classOf<QualifiedIdMapper>())

    lateinit var onParticipantsVideoStateChanged: OnParticipantsVideoStateChanged

    @BeforeTest
    fun setUp() {
        onParticipantsVideoStateChanged = OnParticipantsVideoStateChanged(callRepository, qualifiedIdMapper, callMapper, videoStateChecker)
    }

    @Test
    fun givenCallbackOccurWhenVideoStateChangedThenUpdateParticipantVideoState() {
        given(qualifiedIdMapper).invocation { fromStringToQualifiedID(conversationIdString) }
            .then { conversationIdQualified }

        given(qualifiedIdMapper).invocation { fromStringToQualifiedID(userIdString) }
            .then { userIdQualified }

        given(callMapper).invocation { fromIntToCallingVideoState(videoStateInt) }
            .then { VideoStateCalling.STARTED }


        given(videoStateChecker).invocation { isCameraOn(VideoStateCalling.STARTED) }
            .then { isCameraOn }

        given(callRepository).invocation { updateParticipantCameraStateById(conversationIdString, userIdQualified, clientId, isCameraOn) }
            .thenDoNothing()

        onParticipantsVideoStateChanged.onVideoReceiveStateChanged(conversationIdString, userIdString, clientId, videoStateInt, null)

        verify(qualifiedIdMapper)
            .function(qualifiedIdMapper::fromStringToQualifiedID)
            .with(eq(conversationIdString))
            .wasInvoked(once)

        verify(qualifiedIdMapper)
            .function(qualifiedIdMapper::fromStringToQualifiedID)
            .with(eq(userIdString))
            .wasInvoked(once)

        verify(callMapper)
            .function(callMapper::fromIntToCallingVideoState)
            .with(eq(videoStateInt))
            .wasInvoked(once)

        verify(videoStateChecker)
            .function(videoStateChecker::isCameraOn)
            .with(eq(VideoStateCalling.STARTED))
            .wasInvoked(once)

        verify(callRepository)
            .function(callRepository::updateParticipantCameraStateById)
            .with(eq(conversationIdString), eq(userIdQualified), eq(clientId), eq(isCameraOn))
            .wasInvoked(once)
    }

    companion object {
        const val conversationIdString = "conversationId@domainId"
        val conversationIdQualified = QualifiedID("conversationId", "domainId")
        const val userIdString = "userId@domainId"
        val userIdQualified = QualifiedID("userId", "domainId")
        const val clientId = "client-id"
        const val videoStateInt = 1
        const val isCameraOn = true
    }
}
