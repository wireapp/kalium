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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.persistence.db.DatabaseOptimizer
import com.wire.kalium.util.DelicateKaliumApi

/**
 * Use case responsible for optimizing the local SQLCipher database by invoking `PRAGMA optimize`.
 * This operation analyzes and optimizes indexes, potentially improving the performance of read-heavy queries,
 * especially on large datasets (e.g., conversations and messages).
 *
 * Intended for use in debugging or diagnostic tools (e.g., developer-only settings screens).
 */
interface OptimizeDatabaseUseCase {
    suspend operator fun invoke(): OptimizeDatabaseResult
}

sealed class OptimizeDatabaseResult {
    data object Success : OptimizeDatabaseResult()
    data class Failure(val coreFailure: CoreFailure) : OptimizeDatabaseResult()
}

@DelicateKaliumApi(
    message = "This use case is intended for debugging purposes only and should not be used in production code."
)
internal class OptimizeDatabaseUseCaseImpl constructor(
    private val optimizer: DatabaseOptimizer
) : OptimizeDatabaseUseCase {

    @Suppress("TooGenericExceptionCaught")
    @DelicateKaliumApi(
        message = "This use case is intended for debugging purposes only and should not be used in production code."
    )
    override suspend fun invoke(): OptimizeDatabaseResult {
        return try {
            optimizer.optimize()
            kaliumLogger.i("Database optimization completed successfully")
            OptimizeDatabaseResult.Success
        } catch (e: Exception) {
            kaliumLogger.e("Database optimization failed: ${e.stackTraceToString()}")
            OptimizeDatabaseResult.Failure(CoreFailure.Unknown(e))
        }
    }
}
