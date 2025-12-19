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
import kotlinx.coroutines.flow.transformWhile

public interface ObserveWorkStatusUseCase {
    /**
     * Observes the status of a specific work item identified by the provided [workId].
     *
     * @param workId The unique identifier of the work whose status is being observed.
     * @return A [Flow] emitting the [Work.Status] of the specified work.
     * The flow is closed when the [Work.Status] becomes [Work.Status.Complete]
     *
     * @sample samples.work.WorkUseCases.monitorWorkUntilCompletion
     */
    public suspend operator fun invoke(workId: WorkId): Flow<Work.Status>
}

internal fun ObserveWorkStatusUseCase(repository: WorkRepository): ObserveWorkStatusUseCase = object : ObserveWorkStatusUseCase {
    override suspend fun invoke(
        workId: WorkId
    ): Flow<Work.Status> = repository.observeWork(workId)
        .transformWhile {
            emit(it)
            it !is Work.Status.Complete
        }
}
