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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal interface WorkRepository {
    /**
     * Observes the status of a specific piece of work identified by the given [workId].
     *
     * @param workId The unique identifier of the work whose status is being observed.
     * @return A [Flow] emitting the current [Work.Status] of the specified work or null if no status exists.
     */
    suspend fun observeWork(workId: WorkId): Flow<Work.Status>

    /**
     * Adds a new work item to the repository or updates an existing one.
     *
     * @param work The work instance to be added or updated. It contains the unique identifier, type, and status
     *             information necessary to identify and represent a unit of work.
     */
    suspend fun addOrUpdateWork(work: Work)

    /**
     * Observes and emits a stream of newly added or updated [Work] items.
     *
     * This method provides a [Flow] that emits each new [Work] instance whenever it is introduced
     * in the repository. The emitted [Work] instances include details about their
     * unique identifier ([WorkId]), type ([Work.Type]), and status ([Work.Status]).
     *
     * @return A [Flow] emitting [Work] objects representing new or updated work items.
     */
    suspend fun observeNewWorks(): Flow<Work>
}

@Suppress("FunctionName")
internal fun InMemoryWorkRepository(): WorkRepository = object : WorkRepository {
    private val workMap = MutableStateFlow(mapOf<WorkId, Work>())
    private val newWorksFlow = MutableSharedFlow<Work>()

    override suspend fun observeWork(workId: WorkId): Flow<Work.Status> {
        return workMap.map { it[workId]?.status ?: Work.Status.Complete }.distinctUntilChanged()
    }

    override suspend fun addOrUpdateWork(work: Work) = if (work.status == Work.Status.Complete) {
        workMap.update { currentMap ->
            currentMap - work.id
        }
    } else {
        workMap.update { currentMap ->
            val isNewWork = !currentMap.containsKey(work.id)
            val result = currentMap + (work.id to work)
            if (isNewWork) {
                newWorksFlow.emit(work)
            }
            result
        }
    }

    override suspend fun observeNewWorks(): Flow<Work> {
        return newWorksFlow.map { it }.buffer()
    }
}
