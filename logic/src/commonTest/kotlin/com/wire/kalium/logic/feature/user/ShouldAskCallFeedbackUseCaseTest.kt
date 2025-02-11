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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class ShouldAskCallFeedbackUseCaseTest {

    @Test
    fun givenNoNextTimeForCallFeedbackSaved_whenInvoked_thenTrueIsReturned() = runTest {
        val (_, useCase) = Arrangement().arrange {
            withGetNextTimeForCallFeedback(StorageFailure.DataNotFound.left())
        }

        val result = useCase()

        assertTrue(result)
    }

    @Test
    fun givenNextTimeForCallFeedbackInPast_whenInvoked_thenTrueIsReturned() = runTest {
        val nextTimeToAsk = DateTimeUtil.currentInstant().minus(1.days).toEpochMilliseconds()
        val (_, useCase) = Arrangement().arrange {
            withGetNextTimeForCallFeedback(nextTimeToAsk.right())
        }

        val result = useCase()

        assertTrue(result)
    }

    @Test
    fun givenNextTimeForCallFeedbackInFuture_whenInvoked_thenFalseIsReturned() = runTest {
        val nextTimeToAsk = DateTimeUtil.currentInstant().plus(1.days).toEpochMilliseconds()
        val (_, useCase) = Arrangement().arrange {
            withGetNextTimeForCallFeedback(nextTimeToAsk.right())
        }

        val result = useCase()

        assertFalse(result)
    }

    @Test
    fun givenNextTimeForCallFeedbackIsNegative_whenInvoked_thenFalseIsReturned() = runTest {
        val nextTimeToAsk = -1L
        val (_, useCase) = Arrangement().arrange {
            withGetNextTimeForCallFeedback(nextTimeToAsk.right())
        }

        val result = useCase()

        assertFalse(result)
    }

    private class Arrangement : UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl() {

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, ShouldAskCallFeedbackUseCase> {
            runBlocking { block() }
            return this to ShouldAskCallFeedbackUseCase(userConfigRepository)
        }
    }
}
