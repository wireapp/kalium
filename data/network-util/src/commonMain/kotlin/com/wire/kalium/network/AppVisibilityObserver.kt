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

package com.wire.kalium.network

import io.mockative.Mockable
import kotlinx.coroutines.flow.StateFlow

/**
 * Provides app foreground/background visibility state across platforms.
 *
 * Visibility semantics by platform:
 * - Android: App is in foreground AND screen is on
 * - iOS: App is in foreground (active state)
 * - JVM: Always true (desktop apps don't background)
 */
@Mockable
interface AppVisibilityObserver {
    /**
     * Observes whether the app is currently visible to the user.
     *
     * @return StateFlow<Boolean> where true = visible (foreground), false = hidden (background)
     */
    fun observeAppVisibility(): StateFlow<Boolean>

    companion object {
        const val TAG = "AppVisibilityObserver"
    }
}
