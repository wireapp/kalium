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

package com.wire.kalium.persistence.dao.meeting

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.wire.kalium.persistence.MeetingsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates

internal class MeetingPagingSource(
    private val meetingsQueries: MeetingsQueries,
    private val context: CoroutineContext,
    private val fromDate: Instant,
    private val initialOffset: Long = 0,
) : PagingSource<Int, MeetingDetailsEntity>(), Query.Listener {

    private var currentMeetingQuery: Query<MeetingDetailsEntity>? by Delegates.observable(null) { _, old, new ->
        old?.removeListener(this)
        new?.addListener(this)
    }
    private var currentAvatarsQuery: Query<MeetingParticipantPreviewAssetEntity>? by Delegates.observable(null) { _, old, new ->
        old?.removeListener(this)
        new?.addListener(this)
    }
    override val jumpingSupported: Boolean get() = true

    init {
        registerInvalidatedCallback {
            currentMeetingQuery?.removeListener(this)
            currentMeetingQuery = null
            currentAvatarsQuery?.removeListener(this)
            currentAvatarsQuery = null
        }
    }

    override fun queryResultsChanged() = invalidate()

    @Suppress("CyclomaticComplexMethod", "TooGenericExceptionCaught")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MeetingDetailsEntity> =
        withContext(context) {
            try {
                val key = params.key?.toLong() ?: initialOffset
                val limit = when (params) {
                    is LoadParams.Prepend<*> -> minOf(key, params.loadSize.toLong())
                    else -> params.loadSize.toLong()
                }
                val count = meetingsQueries.countUpcomingMeetingOccurrences(fromDate).awaitAsOne().toInt()
                val offset = when (params) {
                    is LoadParams.Prepend<*> -> maxOf(0, key - params.loadSize).toInt()
                    is LoadParams.Append<*> -> key.toInt()
                    is LoadParams.Refresh<*> -> if (key >= count - params.loadSize) maxOf(0, count - params.loadSize) else key.toInt()
                }
                val meetingQuery = meetingsQueries.selectPagedMeetingDetails(
                    fromDate = fromDate,
                    limit = limit,
                    offset = offset.toLong(),
                    mapper = MeetingMapper::fromViewToDetails
                ).also { currentMeetingQuery = it }
                val meetings = meetingQuery.awaitAsList()
                val participantPreviewAssetIds = loadAndObserveAvatars(meetings)
                val data = meetings.map { meeting ->
                    meeting.withParticipantPreviewAssetIds(
                        participantPreviewAssetIds[meeting.meeting.conversationId].orEmpty()
                    )
                }
                val nextPosition = offset + data.size

                if (invalid) {
                    LoadResult.Invalid()
                } else {
                    LoadResult.Page(
                        data = data,
                        prevKey = offset.takeIf { it > 0 && data.isNotEmpty() },
                        nextKey = nextPosition.takeIf { data.isNotEmpty() && data.size >= limit && it < count },
                        itemsBefore = offset,
                        itemsAfter = maxOf(0, count - nextPosition),
                    )
                }
            } catch (exception: Exception) {
                LoadResult.Error(exception)
            }
        }

    override fun getRefreshKey(state: PagingState<Int, MeetingDetailsEntity>): Int? =
        state.anchorPosition?.let { maxOf(0, it - (state.config.initialLoadSize / 2)) }

    private suspend fun loadAndObserveAvatars(meetings: List<MeetingDetailsEntity>): Map<QualifiedIDEntity, List<QualifiedIDEntity>> {
        val conversationIds = meetings.filter { meeting ->
            when (meeting.conversationType) {
                ConversationEntity.Type.GROUP -> !meeting.isChannel
                ConversationEntity.Type.ONE_ON_ONE -> true
                else -> false
            }
        }.mapTo(mutableSetOf()) { it.meeting.conversationId }

        if (conversationIds.isEmpty()) {
            currentAvatarsQuery = null
            return emptyMap()
        }

        val query = meetingsQueries.selectMeetingParticipantPreviewAssetIds(
            conversationIds = conversationIds.toList(),
            mapper = ::MeetingParticipantPreviewAssetEntity
        )
        currentAvatarsQuery = query
        return query.awaitAsList()
            .groupBy(keySelector = { it.conversationId }, valueTransform = { it.previewAssetId })
            .mapValues { (_, previewAssetIds) -> previewAssetIds.filterNotNull() }
    }
}
