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
package com.wire.kalium.work

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

@DependencyGraph(LongWorkLifetime::class)
internal interface JvmLongWorkGraph : LongWorkGraph {
    @DependencyGraph.Factory
    interface Factory {
        fun create(@Provides inputs: LongWorkInputs): JvmLongWorkGraph
    }
}

internal actual fun createLongWorkGraph(inputs: LongWorkInputs): LongWorkGraph =
    createGraphFactory<JvmLongWorkGraph.Factory>().create(inputs)
