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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.persistence.dao.UserConfigDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TrackingIdentifierStorageImplTest {

    @Test
    fun givenCurrentIdentifier_whenReading_thenValueIsReturnedFromDao() = runTest {
        val (arrangement, storage) = arrangement()
        everySuspend { arrangement.userConfigDAO.getTrackingIdentifier() } returns currentIdentifier

        assertEquals(currentIdentifier, storage.getCurrentTrackingIdentifier())
    }

    @Test
    fun givenNoCurrentIdentifier_whenReading_thenNullIsReturnedFromDao() = runTest {
        val (arrangement, storage) = arrangement()
        everySuspend { arrangement.userConfigDAO.getTrackingIdentifier() } returns null

        assertEquals(null, storage.getCurrentTrackingIdentifier())
    }

    @Test
    fun givenCurrentAndPreviousIdentifiers_whenWriting_thenValuesAreForwardedToDao() = runTest {
        val (arrangement, storage) = arrangement()

        storage.setCurrentTrackingIdentifier(currentIdentifier)
        storage.setPreviousTrackingIdentifier(previousIdentifier)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigDAO.setTrackingIdentifier(eq(currentIdentifier))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigDAO.setPreviousTrackingIdentifier(eq(previousIdentifier))
        }
    }

    @Test
    fun givenDaoFailure_whenWritingIdentifiers_thenFailuresAreCaught() = runTest {
        val (arrangement, storage) = arrangement()
        everySuspend {
            arrangement.userConfigDAO.setTrackingIdentifier(eq(currentIdentifier))
        } throws IllegalStateException("current write failed")
        everySuspend {
            arrangement.userConfigDAO.setPreviousTrackingIdentifier(eq(previousIdentifier))
        } throws IllegalStateException("previous write failed")

        storage.setPreviousTrackingIdentifier(previousIdentifier)
        storage.setCurrentTrackingIdentifier(currentIdentifier)
    }

    @Test
    fun givenDaoFailure_whenReadingCurrentIdentifier_thenFailurePropagates() = runTest {
        val expectedException = IllegalStateException("current read failed")
        val (arrangement, storage) = arrangement()
        everySuspend { arrangement.userConfigDAO.getTrackingIdentifier() } throws expectedException

        val actualException = assertFailsWith<IllegalStateException> {
            storage.getCurrentTrackingIdentifier()
        }

        assertSame(expectedException, actualException)
    }

    private fun arrangement(): Pair<Arrangement, TrackingIdentifierStorage> {
        val arrangement = Arrangement()
        return arrangement to TrackingIdentifierStorageImpl(arrangement.userConfigDAO)
    }

    private class Arrangement {
        val userConfigDAO = mock<UserConfigDAO>(MockMode.autoUnit)
    }

    private companion object {
        const val currentIdentifier = "current-identifier"
        const val previousIdentifier = "previous-identifier"
    }
}
