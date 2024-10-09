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

import com.wire.kalium.calling.VideoStateCalling
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.call.mapper.ParticipantMapperImpl
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import io.mockative.Mock
import io.mockative.any
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ParticipantMapperTest {

    @Mock
    private val videoStateChecker = mock(VideoStateChecker::class)

    @Mock
    private val callMapper = mock(CallMapper::class)

    @Mock
    private val qualifiedIdMapper = mock(QualifiedIdMapper::class)

    private val participantMapperImpl = ParticipantMapperImpl(videoStateChecker, callMapper, qualifiedIdMapper)

    @BeforeTest
    fun setUp() {
        every {
            callMapper.fromIntToCallingVideoState(0)
        }.returns(VideoStateCalling.STOPPED)
        every {
            videoStateChecker.isCameraOn(VideoStateCalling.STOPPED)
        }.returns(false)
        every {
            videoStateChecker.isSharingScreen(VideoStateCalling.STOPPED)
        }.returns(false)
        every { qualifiedIdMapper.fromStringToQualifiedID(any()) }
            .returns(DUMMY_USER_ID)
    }

    @Test
    fun whenMappingToParticipant_withCallMember_thenReturnParticipant() = runTest {
        val participantMap = participantMapperImpl.fromCallMemberToParticipantMinimized(
            member = DUMMY_CALL_MEMBER
        )

        val expectedParticipant = ParticipantMinimized(
            id = QualifiedID(
                value = "dummyId",
                domain = ""
            ),
            userId = QualifiedID(
                value = "dummyId",
                domain = "dummyDomain"
            ),
            clientId = "dummyClientId",
            isMuted = false,
            isCameraOn = false,
            isSharingScreen = false,
            hasEstablishedAudio = false
        )

        assertEquals(expectedParticipant, participantMap)
    }

    companion object {
        private val DUMMY_CALL_MEMBER = CallMember(
            userId = "dummyId",
            clientId = "dummyClientId",
            aestab = 0,
            vrecv = 0,
            isMuted = 0
        )

        private val DUMMY_USER_ID = QualifiedID(
            value = "dummyId",
            domain = "dummyDomain"
        )
    }
}
