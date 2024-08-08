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
package com.wire.kalium.logic.feature.call.scenario

import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.data.call.CallActiveSpeaker
import com.wire.kalium.logic.data.call.CallActiveSpeakers
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapperImpl
import com.wire.kalium.logic.framework.TestUser
import io.mockative.Mock
import io.mockative.any
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class OnActiveSpeakersTest {

    private val testScope = TestScope()

    @Test
    fun givenCallActiveSpeakers_whenOnActiveSpeakersHappens_thenOnlySpeakingParticipantsPassedForward() = testScope.runTest {
        // given
        val speakers = listOf(
            speaker(0, true),
            speaker(1, true),
            speaker(2, true),
            speaker(3, false),
            speaker(4, false)
        )
        val data = Json.encodeToString(CallActiveSpeakers.serializer(), CallActiveSpeakers(speakers))
        val convId = ConversationId("conversation", "domain")
        val expected = mapOf(
            userId(0) to listOf("client0"),
            userId(1) to listOf("client1"),
            userId(2) to listOf("client2")
        )
        val (arrangement, onActiveSpeakers) = Arrangement().arrange()

        // when
        onActiveSpeakers.onActiveSpeakersChanged(Handle(), convId.toString(), data, null)

        // then
        coVerify { arrangement.callRepository.updateParticipantsActiveSpeaker(convId, expected) }
            .wasInvoked(exactly = 1)
    }

    private fun speaker(suffix: Int = 1, isSpeaking: Boolean = false) = CallActiveSpeaker(
        userId = userId(suffix).toString(),
        clientId = "client$suffix",
        audioLevelNow = if (isSpeaking) 10 else 0,
        audioLevel = if (isSpeaking) 10 else 0
    )

    private fun userId(suffix: Int) = QualifiedID("userId$suffix", "some-domain")

    internal class Arrangement {
        @Mock
        val callRepository = mock(CallRepository::class)

        val qualifiedIdMapper = QualifiedIdMapperImpl(TestUser.SELF.id)

        init {
            every {
                callRepository.updateParticipantsActiveSpeaker(any(), any())
            }.returns(Unit)
        }

        fun arrange() = this to OnActiveSpeakers(callRepository, qualifiedIdMapper)
    }
}
