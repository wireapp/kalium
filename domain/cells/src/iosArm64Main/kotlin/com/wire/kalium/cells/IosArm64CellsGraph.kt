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
package com.wire.kalium.cells

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory

@DependencyGraph(CellsFacadeLifetime::class)
internal interface IosArm64CellsGraph : LegacyCellsBindings {
    @DependencyGraph.Factory
    interface Factory {
        fun create(@Provides dependencies: LegacyCellsDependencies, @Provides owner: CellsOwnerPorts): IosArm64CellsGraph
    }
}

internal actual fun createLegacyCellsGraph(dependencies: LegacyCellsDependencies, owner: CellsOwnerPorts): LegacyCellsApi =
    createGraphFactory<IosArm64CellsGraph.Factory>().create(dependencies, owner)
