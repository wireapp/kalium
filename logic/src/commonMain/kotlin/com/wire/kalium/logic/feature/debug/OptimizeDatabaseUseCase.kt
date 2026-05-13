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

@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.debug

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.fold
import com.wire.kalium.persistence.db.DatabaseOptimizer

/**
 * Use case responsible for optimizing the local SQLCipher database by invoking `PRAGMA optimize`.
 * This operation analyzes and optimizes indexes, potentially improving the performance of read-heavy queries,
 * especially on large datasets (e.g., conversations and messages).
 */
public interface OptimizeDatabaseUseCase {
    public suspend operator fun invoke(): OptimizeDatabaseResult
    public suspend fun optimizeAllTables(): OptimizeDatabaseResult
}

public sealed class OptimizeDatabaseResult {
    public data object Success : OptimizeDatabaseResult()
    public data class Failure(val error: StorageFailure) : OptimizeDatabaseResult()
}

internal class OptimizeDatabaseUseCaseImpl(
    private val optimizer: DatabaseOptimizer
) : OptimizeDatabaseUseCase {

    override suspend fun invoke(): OptimizeDatabaseResult = wrapStorageRequest {
        optimizer.optimize()
    }.fold({
        OptimizeDatabaseResult.Failure(it)
    }, {
        OptimizeDatabaseResult.Success
    })

    override suspend fun optimizeAllTables(): OptimizeDatabaseResult = wrapStorageRequest {
        optimizer.optimizeAllTables()
    }.fold({
        OptimizeDatabaseResult.Failure(it)
    }, {
        OptimizeDatabaseResult.Success
    })
}
