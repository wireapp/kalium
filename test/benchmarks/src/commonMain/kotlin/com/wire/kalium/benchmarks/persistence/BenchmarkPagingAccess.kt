package com.wire.kalium.benchmarks.persistence

import androidx.paging.PagingSource
import com.wire.kalium.persistence.dao.message.KaliumPager

@Suppress("UNCHECKED_CAST")
internal fun <Value : Any> KaliumPager<Value>.extractPagingSource(): PagingSource<Any, Value> {
    val field = javaClass.getDeclaredField("pagingSource")
    field.isAccessible = true
    return field.get(this) as PagingSource<Any, Value>
}

@Suppress("UNCHECKED_CAST")
internal suspend fun <Value : Any> KaliumPager<Value>.loadRefreshPage(
    pageSize: Int = MessageReadBenchmarkData.PageSize
): PagingSource.LoadResult.Page<Any, Value> {
    return when (
        val result = extractPagingSource().load(PagingSource.LoadParams.Refresh<Any>(null, pageSize, false))
    ) {
        is PagingSource.LoadResult.Page<*, *> -> result as PagingSource.LoadResult.Page<Any, Value>
        else -> error("Unexpected paging result: $result")
    }
}
