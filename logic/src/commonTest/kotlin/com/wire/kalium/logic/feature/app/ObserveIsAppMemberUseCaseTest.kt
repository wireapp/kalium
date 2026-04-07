/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.app

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.app.AppRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import io.mockative.coEvery
import io.mockative.eq
import io.mockative.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

class ObserveIsAppMemberUseCaseTest {

    @Test
    fun givenAppIdAndConversationId_whenObservingIsAppMember_thenReturnAppId() = runTest {
        // given
        val (_, observeIsAppMember) = Arrangement()
            .withObserveIsAppMemberSuccess(
                appId = Arrangement.APP_ID,
                conversationId = Arrangement.CONVERSATION_ID
            )
            .arrange()

        // when
        val result = observeIsAppMember.invoke(
            appId = Arrangement.APP_ID,
            conversationId = Arrangement.CONVERSATION_ID
        ).first()

        // then
        assertIs<ObserveIsAppMemberResult.Success>(result)
        assertEquals(Arrangement.APP_ID, result.userId)
    }

    private class Arrangement {
        private val appRepository = mock(AppRepository::class)

        private val useCase: ObserveIsAppMemberUseCase = ObserveIsAppMemberUseCaseImpl(
            appRepository = appRepository
        )

        suspend fun withObserveIsAppMemberSuccess(
            appId: QualifiedID,
            conversationId: ConversationId
        ) = apply {
            coEvery {
                appRepository.observeIsAppMember(eq(appId), eq(conversationId))
            }.returns(flowOf(Either.Right(APP_ID)))
        }

        fun arrange() = this to useCase

        companion object {
            val APP_ID = QualifiedID(
                value = Uuid.random().toString(),
                domain = "wire.com"
            )

            val CONVERSATION_ID = QualifiedID(
                value = Uuid.random().toString(),
                domain = "wire.com"
            )
        }
    }
}