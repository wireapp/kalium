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

package com.wire.kalium.logic.notificationextension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppleNotificationExtensionResourceTeardownTest {
    @Test
    fun givenFailureAtEachConstructionStep_whenRollingBack_thenEveryAcquiredResourceClosesInReverseOrder() {
        repeat(3) { failingStep ->
            val rollback = AppleNotificationExtensionConstructionRollback()
            val timeline = mutableListOf<Int>()
            val failure = IllegalStateException("construction-step-$failingStep")

            val thrown = assertFailsWith<IllegalStateException> {
                repeat(3) { step ->
                    if (step == failingStep) rollback.rollback(failure)
                    rollback.own { timeline += step }
                }
            }

            assertEquals(failure, thrown)
            assertEquals((0 until failingStep).toList().reversed(), timeline)
        }
    }

    @Test
    fun givenConstructionRollbackCloseFails_whenRollingBack_thenUnsafeFailureIsPropagated() {
        val rollback = AppleNotificationExtensionConstructionRollback()
        rollback.own { error("close-failed") }

        val thrown = assertFailsWith<NotificationExtensionLogicBridgeUnsafeTeardownException> {
            rollback.rollback(IllegalArgumentException("construction-failed"))
        }

        assertEquals(1, thrown.suppressedExceptions.size)
    }

    @Test
    fun givenCryptoConstructionFails_whenRollingBack_thenProcessingStopsBeforeCryptoProvidersClose() {
        val rollback = AppleNotificationExtensionConstructionRollback()
        val timeline = mutableListOf<String>()
        rollback.ownFirst { timeline += "processing-closed" }
        rollback.own { timeline += "mls-closed" }
        rollback.own { timeline += "proteus-closed" }

        assertFailsWith<IllegalStateException> {
            rollback.rollback(IllegalStateException("construction-failed"))
        }

        assertEquals(
            listOf("processing-closed", "proteus-closed", "mls-closed"),
            timeline
        )
    }

    @Test
    fun givenCryptoCloseFails_whenClosingBridgeResources_thenEveryOwnedResourceIsStillClosedInOrder() {
        val timeline = mutableListOf<String>()

        assertFailsWith<IllegalStateException> {
            closeAppleNotificationExtensionOwnedResources(
                closeProcessing = { timeline += "processing-closed" },
                closeCrypto = {
                    timeline += "crypto-closed"
                    error("synthetic-close-failure")
                },
                closeNotificationSocket = { timeline += "socket-closed" },
                removeAuthenticatedNetwork = { timeline += "network-removed" },
                closeAuthenticatedNetwork = { timeline += "network-closed" },
                removeUserStorage = { timeline += "storage-removed" },
                closeUserDatabase = { timeline += "database-closed" }
            )
        }

        assertEquals(
            listOf(
                "processing-closed",
                "crypto-closed",
                "socket-closed",
                "network-removed",
                "network-closed",
                "storage-removed",
                "database-closed"
            ),
            timeline
        )
    }
}
