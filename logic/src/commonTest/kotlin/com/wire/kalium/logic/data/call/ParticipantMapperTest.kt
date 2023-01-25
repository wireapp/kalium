/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
