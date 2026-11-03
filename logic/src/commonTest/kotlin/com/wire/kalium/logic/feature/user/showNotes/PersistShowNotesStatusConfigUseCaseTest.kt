/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.user.showNotes

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PersistShowNotesStatusConfigUseCaseTest {

    @Test
    fun givenATrueValue_shouldCallSetShowNotesEnabled() = runTest {
        val repository = mock<UserPropertyRepository>()
        everySuspend { repository.setShowNotesEnabled() } returns Either.Right(Unit)

        val actual = PersistShowNotesStatusConfigUseCaseImpl(repository)(true)

        verifySuspend(VerifyMode.exactly(1)) { repository.setShowNotesEnabled() }
        assertTrue(actual is ShowNotesConfigResult.Success)
    }

    @Test
    fun givenAFalseValue_shouldCallDeleteShowNotesProperty() = runTest {
        val repository = mock<UserPropertyRepository>()
        everySuspend { repository.deleteShowNotesProperty() } returns Either.Right(Unit)

        val actual = PersistShowNotesStatusConfigUseCaseImpl(repository)(false)

        verifySuspend(VerifyMode.exactly(1)) { repository.deleteShowNotesProperty() }
        assertTrue(actual is ShowNotesConfigResult.Success)
    }

    @Test
    fun givenRepositoryFails_shouldReturnFailureResult() = runTest {
        val repository = mock<UserPropertyRepository>()
        everySuspend { repository.setShowNotesEnabled() } returns Either.Left(CoreFailure.Unknown(RuntimeException("Some error")))

        val actual = PersistShowNotesStatusConfigUseCaseImpl(repository)(true)

        assertTrue(actual is ShowNotesConfigResult.Failure)
    }
}
