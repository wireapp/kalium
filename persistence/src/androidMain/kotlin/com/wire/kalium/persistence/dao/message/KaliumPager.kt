package com.wire.kalium.persistence.dao.message

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

/**
 * Exposes a [pagingDataFlow] that can be used in Android UI components to display paginated data.
 */
class KaliumPager<EntityType : Any>(
    private val pager: Pager<Int, EntityType>,
    internal val pagingSource: PagingSource<Int, EntityType>
) {
    val pagingDataFlow: Flow<PagingData<EntityType>>
        get() = pager.flow
}
