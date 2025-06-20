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
package com.wire.kalium.logic.util

import io.mockative.Mockable
import kotlin.time.Duration

@Mockable
interface ExponentialDurationHelper {
    fun reset()
    fun next(): Duration
}

class ExponentialDurationHelperImpl(
    private val initialDuration: Duration,
    private val maxDuration: Duration,
    private val factor: Double = 2.0,
) : ExponentialDurationHelper {
    private var currentDuration = initialDuration

    override fun reset() {
        currentDuration = initialDuration
    }

    override fun next(): Duration = currentDuration.also {
        currentDuration = currentDuration.times(factor).coerceAtMost(maxDuration)
    }
}
