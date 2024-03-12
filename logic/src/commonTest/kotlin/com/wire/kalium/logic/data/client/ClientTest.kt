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
package com.wire.kalium.logic.data.client

import com.wire.kalium.logic.framework.TestClient
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class ClientTest {

    @Test
    fun givenLastActiveIsNull_thenIsActiveIsFalse() {
        val client = TestClient.CLIENT.copy(
            lastActive = null
        )
        assertFalse(client.isActive)
    }

    @Test
    fun givenLastActiveIsOlderThanInactivityDuration_thenIsActiveIsFalse() {
        val client = TestClient.CLIENT.copy(
            lastActive = Clock.System.now() - (Client.INACTIVE_DURATION + 1.days)
        )
        assertFalse(client.isActive)
    }

    @Test
    fun givenLastActiveIsNewerThanInactivityDuration_thenIsActiveIsTrue() {
        val client = TestClient.CLIENT.copy(
            lastActive = Clock.System.now() - (Client.INACTIVE_DURATION - 1.days)
        )
        assertTrue(client.isActive)
    }

    @Test
    fun givenLastActiveIsNull_whenRegistrationTRimeIsNotOld_thenIsActiveIsTrue() {
        val client = TestClient.CLIENT.copy(
            lastActive = null,
            registrationTime = Clock.System.now() - (Client.INACTIVE_DURATION - 1.days)
        )
        assertTrue(client.isActive)
    }

    @Test
    fun givenLastActiveIsNull_whenRegistrationTRimeIsOld_thenIsActiveIsFalse() {
        val client = TestClient.CLIENT.copy(
            lastActive = null,
            registrationTime = Clock.System.now() - (Client.INACTIVE_DURATION + 1.days)
        )
        assertFalse(client.isActive)
    }

    @Test
    fun givenLastActiveIsNull_whenRegistrationTRimeIsNull_thenIsActiveIsFalse() {
        val client = TestClient.CLIENT.copy(
            lastActive = null,
            registrationTime = null
        )
        assertFalse(client.isActive)
    }

}
