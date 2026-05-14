package com.wire.kalium.benchmarks.persistence

import androidx.paging.PagingSource
import com.wire.kalium.persistence.dao.message.KaliumPager

@Suppress("UNCHECKED_CAST")
internal fun <Value : Any> KaliumPager<Value>.extractPagingSource(): PagingSource<Int, Value> {
    val field = javaClass.getDeclaredField("pagingSource")
    field.isAccessible = true
    return field.get(this) as PagingSource<Int, Value>
}

internal suspend fun <Value : Any> KaliumPager<Value>.loadRefreshPage(
    pageSize: Int = MessageReadBenchmarkData.PageSize
): PagingSource.LoadResult.Page<Int, Value> {
    return when (
        val result = extractPagingSource().load(PagingSource.LoadParams.Refresh(null, pageSize, false))
    ) {
        is PagingSource.LoadResult.Page<Int, Value> -> result
        else -> error("Unexpected paging result: $result")
    }
}
