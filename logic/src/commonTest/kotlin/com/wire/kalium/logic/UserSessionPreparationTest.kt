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

package com.wire.kalium.logic

import com.wire.kalium.userstorage.di.UserStoragePreparationFailure
import kotlin.test.Test
import kotlin.test.assertEquals

class UserSessionPreparationTest {
    @Test
    fun givenInsufficientStorage_whenMappingFailure_thenInsufficientStorageIsReturned() {
        val result = UserStoragePreparationFailure.InsufficientStorage.toUserSessionPreparationFailure()

        assertEquals(UserSessionPreparationFailure.InsufficientStorage, result)
    }

    @Test
    fun givenTemporarilyUnavailable_whenMappingFailure_thenTemporarilyUnavailableIsReturned() {
        val result = UserStoragePreparationFailure.TemporarilyUnavailable.toUserSessionPreparationFailure()

        assertEquals(UserSessionPreparationFailure.TemporarilyUnavailable, result)
    }

    @Test
    fun givenApplicationUpdateRequired_whenMappingFailure_thenApplicationUpdateRequiredIsReturned() {
        val result = UserStoragePreparationFailure.ApplicationUpdateRequired.toUserSessionPreparationFailure()

        assertEquals(UserSessionPreparationFailure.ApplicationUpdateRequired, result)
    }

    @Test
    fun givenSupportRequired_whenMappingFailure_thenSupportRequiredIsReturned() {
        val result = UserStoragePreparationFailure.SupportRequired.toUserSessionPreparationFailure()

        assertEquals(UserSessionPreparationFailure.SupportRequired, result)
    }
}
