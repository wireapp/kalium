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

package com.wire.kalium.persistence.dao.message

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import com.wire.kalium.persistence.db.ReadDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * Exposes a [pagingDataFlow] that can be used in Android UI components to display paginated data.
 */
class KaliumPager<EntityType : Any>(
    private val pager: Pager<Int, EntityType>,
    internal val pagingSource: PagingSource<Int, EntityType>,
    private val readDispatcher: ReadDispatcher,
    private val invalidateOn: Flow<*>? = null,
    private val invalidatePagingSource: (() -> Unit)? = null,
) {
    val pagingDataFlow: Flow<PagingData<EntityType>>
        get() {
            val invalidationFlow = invalidateOn
            val invalidate = invalidatePagingSource
            return when {
                invalidationFlow == null || invalidate == null -> pager.flow.flowOn(readDispatcher.value)
                else -> channelFlow {
                    val invalidationJob = launch {
                        invalidationFlow.collect { invalidate() }
                    }
                    try {
                        pager.flow.collect { send(it) }
                    } finally {
                        invalidationJob.cancel()
                    }
                }.flowOn(readDispatcher.value)
            }
        }
}
