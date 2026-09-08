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
package com.wire.kalium.benchmarks.fixtures

import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal suspend fun <T : Any> firstPage(flow: Flow<PagingData<T>>): List<T> = withTimeout(60_000L) {
    coroutineScope {
        val loaded = CompletableDeferred<List<T>>()
        val presenter = object : PagingDataPresenter<T>(Dispatchers.Unconfined) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<T>) {
                if (event is PagingDataEvent.Refresh) loaded.complete(snapshot().items)
            }
        }
        val collection = launch { flow.collectLatest(presenter::collectFrom) }
        val errors = launch {
            presenter.loadStateFlow.collect { states ->
                (states?.refresh as? LoadState.Error)?.let { loaded.completeExceptionally(it.error) }
            }
        }
        try {
            loaded.await()
        } finally {
            collection.cancelAndJoin()
            errors.cancelAndJoin()
        }
    }
}
