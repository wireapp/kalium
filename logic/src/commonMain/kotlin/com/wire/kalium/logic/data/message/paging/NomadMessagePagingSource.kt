/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.message.paging

import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadParamsAppend
import app.cash.paging.PagingSourceLoadParamsRefresh
import app.cash.paging.PagingSourceLoadResult
import app.cash.paging.PagingSourceLoadResultPage
import app.cash.paging.PagingState
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.common.logger.kaliumLogger

internal class NomadMessagePagingSource(
    private val delegate: PagingSource<Int, MessageEntity>,
    private val onEmptyAppend: suspend (PagingSource<Int, MessageEntity>) -> Unit,
) : PagingSource<Int, MessageEntity>() {

    init {
        delegate.registerInvalidatedCallback { invalidate() }
    }

    override suspend fun load(
        params: PagingSourceLoadParams<Int>
    ): PagingSourceLoadResult<Int, MessageEntity> {
        val result = delegate.load(params)
        logLoad(params, result)
        if (result is PagingSourceLoadResultPage<Int, MessageEntity> &&
            shouldTriggerRemoteFetch(params, result)
        ) {
            onEmptyAppend(this)
        }
        return result
    }

    private fun logLoad(
        params: PagingSourceLoadParams<Int>,
        result: PagingSourceLoadResult<Int, MessageEntity>
    ) {
        when (result) {
            is PagingSourceLoadResultPage<Int, MessageEntity> -> {
                kaliumLogger.d(
                    "[$TAG] load=${params::class.simpleName} key=${params.key} size=${params.loadSize} " +
                        "data=${result.data.size} prevKey=${result.prevKey} nextKey=${result.nextKey}"
                )
            }

            else -> {
                kaliumLogger.d(
                    "[$TAG] load=${params::class.simpleName} key=${params.key} size=${params.loadSize} " +
                        "result=${result::class.simpleName}"
                )
            }
        }
    }

    private fun shouldTriggerRemoteFetch(
        params: PagingSourceLoadParams<Int>,
        result: PagingSourceLoadResultPage<Int, MessageEntity>
    ): Boolean {
        val shouldTrigger = when (params) {
            is PagingSourceLoadParamsAppend<*> -> result.nextKey == null
            is PagingSourceLoadParamsRefresh<*> -> result.nextKey == null
            else -> false
        }
        if (shouldTrigger) {
            kaliumLogger.d(
                "[$TAG] Paging boundary reached. loadType=${params::class.simpleName} " +
                    "dataSize=${result.data.size} nextKey=${result.nextKey}"
            )
        }
        return shouldTrigger
    }

    companion object {
        const val TAG = "NomadMessagePagingSource"
    }

    override fun getRefreshKey(state: PagingState<Int, MessageEntity>): Int? =
        delegate.getRefreshKey(state)
}
