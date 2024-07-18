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
package com.wire.kalium.logic.feature.analytics

import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import io.mockative.coVerify
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class DeletePreviousTrackingIdentifierUseCaseTest {

    @Test
    fun givenExistingPreviousTrackingIdentifier_whenDeletingPreviousTrackingIdentifier_thenItIsCorrectlyDeleted() = runTest {
        // given
        val (arrangement, useCase) = Arrangement().arrange {
            withGetPreviousTrackingIdentifier(PREVIOUS_IDENTIFIER)
        }

        // when
        useCase.invoke()

        // then
        coVerify {
            arrangement.userConfigRepository.deletePreviousTrackingIdentifier()
        }.wasInvoked(exactly = once)
    }

    private companion object {
        const val PREVIOUS_IDENTIFIER = "abcd-1234"
    }

    private class Arrangement : UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl() {

        private val useCase: DeletePreviousTrackingIdentifierUseCase = DeletePreviousTrackingIdentifierUseCase(
            userConfigRepository = userConfigRepository
        )

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, DeletePreviousTrackingIdentifierUseCase> {
            runBlocking { block() }
            return this to useCase
        }
    }
}
