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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * iOS implementation of AppVisibilityObserver.
 *
 * TODO: Implement iOS app lifecycle observation
 * Currently returns a placeholder that always reports visible = true
 */
internal actual class AppVisibilityObserverImpl : AppVisibilityObserver {

    override fun observeAppVisibility(): StateFlow<Boolean> =
        MutableStateFlow(true) // TODO: Implement iOS app lifecycle observation
}
