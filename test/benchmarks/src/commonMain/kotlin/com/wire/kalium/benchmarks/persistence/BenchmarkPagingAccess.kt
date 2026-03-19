package com.wire.kalium.benchmarks.persistence

import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParamsRefresh
import app.cash.paging.PagingSourceLoadResultPage
import com.wire.kalium.persistence.dao.message.KaliumPager

@Suppress("UNCHECKED_CAST")
internal fun <Value : Any> KaliumPager<Value>.extractPagingSource(): PagingSource<Int, Value> {
    val field = javaClass.getDeclaredField("pagingSource")
    field.isAccessible = true
    return field.get(this) as PagingSource<Int, Value>
}

internal suspend fun <Value : Any> KaliumPager<Value>.loadRefreshPage(
    pageSize: Int = MessageReadBenchmarkData.PageSize
): PagingSourceLoadResultPage<Int, Value> {
    return when (
        val result = extractPagingSource().load(PagingSourceLoadParamsRefresh<Int>(null, pageSize, false))
    ) {
        is PagingSourceLoadResultPage<Int, Value> -> result
        else -> error("Unexpected paging result: $result")
    }
}
