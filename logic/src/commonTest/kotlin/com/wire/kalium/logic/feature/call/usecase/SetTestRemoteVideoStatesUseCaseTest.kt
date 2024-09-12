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

import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.feature.call.CallManager
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class SetTestRemoteVideoStatesUseCaseTest {

    @Mock
    private val callManager = mock(CallManager::class)

    private lateinit var setTestRemoteVideoStates: SetTestRemoteVideoStatesUseCase

    @BeforeTest
    fun setup() = runBlocking {
        setTestRemoteVideoStates = SetTestRemoteVideoStatesUseCase(lazy { callManager })

        coEvery {
            callManager.setTestPreviewActive(any())
        }.returns(Unit)
    }

    @Test
    fun givenWhenSetTestRemoteVideoStates_thenUpdateTestRemoteVideoStates() = runTest {
        setTestRemoteVideoStates(CONVERSATION_ID, listOf(DUMMY_PARTICIPANT))

        coVerify {
            callManager.setTestRemoteVideoStates(eq(CONVERSATION_ID), eq(listOf(DUMMY_PARTICIPANT)))
        }.wasInvoked(once)
    }

    companion object {
        private val CONVERSATION_ID = ConversationId("conversation", "wire.com")
        private val DUMMY_PARTICIPANT = Participant(
            id = QualifiedID(
                value = "dummyId",
                domain = "dummyDomain"
            ),
            clientId = "dummyClientId",
            isMuted = false,
            isSpeaking = false,
            isCameraOn = false,
            isSharingScreen = false,
            hasEstablishedAudio = true,
            accentId = 0
        )
    }
}
