/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.user.showNotes

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObserveShowNotesEnabledUseCaseTest {

    @Test
    fun givenShowNotesIsEnabled_whenObserving_thenShouldEmitTrue() = runTest {
        val repository = mock<UserPropertyRepository>()
        every { repository.observeShowNotesStatus() } returns flowOf(Either.Right(true))

        ObserveShowNotesEnabledUseCaseImpl(repository)().test {
            assertTrue(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenShowNotesIsDisabled_whenObserving_thenShouldEmitFalse() = runTest {
        val repository = mock<UserPropertyRepository>()
        every { repository.observeShowNotesStatus() } returns flowOf(Either.Right(false))

        ObserveShowNotesEnabledUseCaseImpl(repository)().test {
            assertFalse(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenStorageFailure_whenObserving_thenShouldFallBackToDisabled() = runTest {
        val repository = mock<UserPropertyRepository>()
        every { repository.observeShowNotesStatus() } returns flowOf(Either.Left(StorageFailure.DataNotFound))

        ObserveShowNotesEnabledUseCaseImpl(repository)().test {
            assertFalse(awaitItem())
            awaitComplete()
        }
    }
}
