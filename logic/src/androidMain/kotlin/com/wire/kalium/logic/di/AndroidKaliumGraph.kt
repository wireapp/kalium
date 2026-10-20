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

package com.wire.kalium.logic.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

@DependencyGraph(KaliumLifetime::class)
internal interface AndroidKaliumGraph : KaliumGraph {
    @DependencyGraph.Factory
    interface Factory {
        fun create(@Provides inputs: GlobalGraphInputs, @Provides owner: GlobalGraphOwner): AndroidKaliumGraph
    }
}

internal actual fun createKaliumGraph(inputs: GlobalGraphInputs, owner: GlobalGraphOwner): KaliumGraph =
    createGraphFactory<AndroidKaliumGraph.Factory>().create(inputs, owner)
