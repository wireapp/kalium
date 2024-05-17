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

import app.cash.paging.Pager
import app.cash.paging.PagingData
import app.cash.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.CoroutineContext

/**
 * Exposes a [pagingDataFlow] that can be used in Android UI components to display paginated data.
 */
class KaliumPager<EntityType : Any>(
    private val pager: Pager<Int, EntityType>,
    internal val pagingSource: PagingSource<Int, EntityType>,
    private val coroutineContext: CoroutineContext
) {
    val pagingDataFlow: Flow<PagingData<EntityType>>
        get() = pager.flow.flowOn(coroutineContext)
}
