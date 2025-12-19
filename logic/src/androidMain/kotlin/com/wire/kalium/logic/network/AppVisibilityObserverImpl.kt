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

package com.wire.kalium.logic.network

import com.wire.kalium.network.AppVisibilityObserver
import kotlinx.coroutines.flow.StateFlow

/**
 * Android implementation of AppVisibilityObserver.
 *
 * Delegates to the app-provided visibility flow from CurrentScreenManager,
 * which combines:
 * - App lifecycle state (foreground/background)
 * - Screen power state (on/off)
 *
 * @param visibilityFlow StateFlow from CurrentScreenManager.isAppVisibleFlow()
 */
internal actual class AppVisibilityObserverImpl(
    private val visibilityFlow: StateFlow<Boolean>
) : AppVisibilityObserver {

    override fun observeAppVisibility(): StateFlow<Boolean> = visibilityFlow
}
