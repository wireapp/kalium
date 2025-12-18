/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.persistence.db

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DbInvalidationControllerTest {

    private lateinit var notifiedKeys: MutableList<String>

    private fun controller(enabled: Boolean): DbInvalidationController {
        notifiedKeys = mutableListOf()
        return DbInvalidationController(
            enabled = enabled,
            notifyKey = { key -> notifiedKeys += key }
        )
    }

    @Test
    fun givenFeatureDisabled_whenRunningMuted_thenNotificationsAreNotMuted() = runTest {
        val controller = controller(enabled = false)

        controller.runMuted {
            controller.onNotify(arrayOf("A"))
            controller.onNotify(arrayOf("B"))
        }

        assertEquals(listOf("A", "B"), notifiedKeys)
    }

    @Test
    fun givenControllerEnabledAndNotMuted_whenNotifyIsCalled_thenNotificationsPassThrough() {
        val controller = controller(enabled = true)

        controller.onNotify(arrayOf("A", "B"))

        assertEquals(listOf("A", "B"), notifiedKeys)
    }

    @Test
    fun givenMutedScope_whenMultipleNotificationsOccur_thenTheyAreAggregatedAndFlushedOnce() = runTest {
        val controller = controller(enabled = true)

        controller.runMuted {
            controller.onNotify(arrayOf("A"))
            controller.onNotify(arrayOf("B", "A"))
            controller.onNotify(arrayOf("C"))

            assertTrue(
                notifiedKeys.isEmpty(),
                "No notifications should be forwarded while muted"
            )
        }

        assertEquals(setOf("A", "B", "C"), notifiedKeys.toSet())
    }

    @Test
    fun givenNestedMutedScopes_whenNotificationsOccur_thenFlushHappensOnlyAfterOutermostScope() = runTest {
        val controller = controller(enabled = true)

        controller.runMuted {
            controller.onNotify(arrayOf("A"))
            controller.runMuted {
                controller.onNotify(arrayOf("B"))
            }
            controller.onNotify(arrayOf("C"))
        }

        assertEquals(setOf("A", "B", "C"), notifiedKeys.toSet())
        assertEquals(3, notifiedKeys.size)
    }

    @Test
    fun givenMutedScopeCompleted_whenNewNotificationOccurs_thenItIsForwardedImmediately() = runTest {
        val controller = controller(enabled = true)

        controller.runMuted {
            controller.onNotify(arrayOf("A"))
        }

        controller.onNotify(arrayOf("B"))

        assertEquals(listOf("A", "B"), notifiedKeys)
    }
}
