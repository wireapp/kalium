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

import kotlinx.coroutines.flow.Flow

public interface ObserveNewWorkUseCase {
    /**
     * Observes and emits a stream of newly added work items.
     *
     * This method provides a flow of work instances representing new work items.
     * Each emitted work instance contains details such as its unique identifier, type, and status.
     *
     * @return A [Flow] emitting [Work] objects representing new work items.
     * @sample samples.work.WorkUseCases.observeNewWorks
     */
    public suspend operator fun invoke(): Flow<Work>
}

internal fun ObserveNewWorkUseCase(repository: WorkRepository): ObserveNewWorkUseCase = object : ObserveNewWorkUseCase {
    override suspend fun invoke(): Flow<Work> = repository.observeNewWorks()
}
