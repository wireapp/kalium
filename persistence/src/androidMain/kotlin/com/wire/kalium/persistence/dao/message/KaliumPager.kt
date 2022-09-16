package com.wire.kalium.persistence.dao.message

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Exposes a [pagingDataFlow] that can be used in Android UI components to display paginated data.
 */
class KaliumPager<EntityType : Any>(
    private val pager: Pager<Long, EntityType>,
    internal val pagingSource: PagingSource<Long, EntityType>
) {
    val pagingDataFlow: Flow<PagingData<EntityType>>
        get() = pager.flow
}
