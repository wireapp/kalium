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
internal class KeyedQueryPagingSource<Key : Any, RowType : Any>(
    private val queryProvider: (beginInclusive: Key, endExclusive: Key?) -> Query<RowType>,
    private val pageBoundariesProvider: (anchor: Key?, limit: Long) -> Query<Key>,
    private val transacter: Transacter,
    private val dispatcher: CoroutineDispatcher,
) : QueryPagingSource<Key, RowType>() {

    private var pageBoundaries: List<Key>? = null
    override val jumpingSupported: Boolean get() = false

    override fun getRefreshKey(state: PagingState<Key, RowType>): Key? {
        val boundaries = pageBoundaries ?: return null
        val last = state.pages.lastOrNull() ?: return null
        val keyIndexFromNext = last.nextKey?.let { boundaries.indexOf(it) - 1 }
        val keyIndexFromPrev = last.prevKey?.let { boundaries.indexOf(it) + 1 }
        val keyIndex = keyIndexFromNext ?: keyIndexFromPrev ?: return null

        return boundaries.getOrNull(keyIndex)
    }

    override suspend fun load(params: LoadParams<Key>): LoadResult<Key, RowType> {
        return withContext(dispatcher) {
            try {
                transacter.transactionWithResult {
                    val boundaries = pageBoundaries
                        ?: pageBoundariesProvider(params.key, params.loadSize.toLong())
                            .executeAsList()
                            .also { pageBoundaries = it }

                    val key = params.key ?: boundaries.first()

                    require(key in boundaries)

                    val keyIndex = boundaries.indexOf(key)
                    val previousKey = boundaries.getOrNull(keyIndex - 1)
                    val nextKey = boundaries.getOrNull(keyIndex + 1)
                    val results = queryProvider(key, nextKey)
                        .also { currentQuery = it }
                        .executeAsList()

                    LoadResult.Page(
                        data = results,
                        prevKey = previousKey,
                        nextKey = nextKey,
                    )
                }
            } catch (e: Exception) {
                if (e is IllegalArgumentException) throw e
                LoadResult.Error(e)
            }
        }
    }
}
