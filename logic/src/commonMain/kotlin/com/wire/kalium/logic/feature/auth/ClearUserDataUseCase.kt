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

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.di.UserStorage
import io.mockative.Mockable

/**
 * Clears the user data from the local storage, except for the client id
 */
@Mockable
interface ClearUserDataUseCase {
    suspend operator fun invoke()
}

internal class ClearUserDataUseCaseImpl internal constructor(
    private val userStorage: UserStorage
) : ClearUserDataUseCase {

    override suspend operator fun invoke() {
        clearUserStorage()
    }

    private fun clearUserStorage() {
        userStorage.database.nuke()
        // exclude clientId clear from this step
        userStorage.preferences.clear()
    }
}
