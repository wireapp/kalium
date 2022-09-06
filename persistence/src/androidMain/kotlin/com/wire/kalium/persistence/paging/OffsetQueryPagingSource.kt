package com.wire.kalium.persistence.paging

import androidx.paging.PagingState
import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Original source https://github.com/cashapp/sqldelight/tree/master/extensions/android-paging3/src/main/java/app/cash/sqldelight/paging3
 * Modified to fix instabilities and crashes on our setup.
 *
 * Remove and use SQLDelight's implementation once the following issues are fixed and a patch is released:
 * - https://github.com/cashapp/sqldelight/issues/2591
 * - https://github.com/cashapp/sqldelight/issues/2434
 */
internal class OffsetQueryPagingSource<RowType : Any>(
    private val queryProvider: (limit: Long, offset: Long) -> Query<RowType>,
    private val countQuery: Query<Long>,
    private val transacter: Transacter,
    private val dispatcher: CoroutineDispatcher,
) : QueryPagingSource<Long, RowType>() {

    override val jumpingSupported get() = true

    override suspend fun load(
        params: LoadParams<Long>
    ): LoadResult<Long, RowType> = withContext(dispatcher) {
        try {
            val keyParameter = params.key ?: 0L
            transacter.transactionWithResult {
                val count = countQuery.executeAsOne()
                val key = if (keyParameter >= count) {
                    maxOf(0, count - params.loadSize)
                } else {
                    keyParameter
                }

                val loadSize = if (key < 0) params.loadSize + key else params.loadSize

                val data = queryProvider(loadSize.toLong(), maxOf(0, key))
                    .also { currentQuery = it }
                    .executeAsList()

                LoadResult.Page(
                    data = data,
                    // allow one, and only one negative prevKey in a paging set. This is done for
                    // misaligned prepend queries to avoid duplicates.
                    prevKey = if (key <= 0L) null else key - params.loadSize,
                    nextKey = if (key + params.loadSize >= count) null else key + params.loadSize,
                    itemsBefore = maxOf(0L, key).toInt(),
                    itemsAfter = maxOf(0, (count - (key + params.loadSize))).toInt()
                )
            }
        } catch (e: Exception) {
            if (e is IndexOutOfBoundsException) throw e
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Long, RowType>) = null
}
